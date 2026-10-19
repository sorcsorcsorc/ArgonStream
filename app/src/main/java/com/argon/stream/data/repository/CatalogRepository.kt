package com.argon.stream.data.repository

import com.argon.stream.core.network.NetworkResult
import com.argon.stream.core.network.safeApiCall
import com.argon.stream.core.security.SecretVault
import com.argon.stream.data.local.WatchHistoryDao
import com.argon.stream.data.local.WatchedDao
import com.argon.stream.data.local.WatchedEntity
import com.argon.stream.data.local.WatchlistDao
import com.argon.stream.data.local.WatchlistEntity
import com.argon.stream.data.remote.cinemeta.CinemetaClient
import com.argon.stream.data.remote.cinemeta.CinemetaMetaDto
import com.argon.stream.data.remote.tmdb.TmdbApiService
import com.argon.stream.data.remote.tmdb.TmdbDetailsDto
import com.argon.stream.data.remote.tmdb.TmdbMediaDto
import com.argon.stream.data.remote.wikipedia.WikidataCastClient
import com.argon.stream.data.remote.kitsu.KitsuAnimeClient
import com.argon.stream.data.remote.imdb.ImdbSuggestClient
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.argon.stream.data.remote.wikipedia.WikipediaPhotoClient
import com.argon.stream.domain.model.CastMember
import com.argon.stream.domain.model.CatalogGenres
import com.argon.stream.domain.model.CatalogKind
import com.argon.stream.domain.model.CatalogPage
import com.argon.stream.domain.model.CatalogQuery
import com.argon.stream.domain.model.CatalogRow
import com.argon.stream.domain.model.EpisodeItem
import com.argon.stream.domain.model.MediaDetails
import com.argon.stream.domain.model.MediaItem
import com.argon.stream.domain.model.MediaType
import com.argon.stream.domain.model.SeasonSummary
import com.argon.stream.domain.model.WatchProgressItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Catalog + metadata source of truth.
 *
 * - With a TMDB key: TMDB catalogs/search/details (ids are numeric TMDB ids).
 * - Without a key: Cinemeta, Stremio's free public catalog (ids are IMDb "tt" ids).
 *
 * Both id styles are accepted by [getMediaDetails], so Continue Watching and Watchlist
 * entries keep working when the user adds or removes a TMDB key later.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CatalogRepository @Inject constructor(
    private val tmdbApi: TmdbApiService,
    private val cinemeta: CinemetaClient,
    private val wikipediaPhotos: WikipediaPhotoClient,
    private val wikidataCast: WikidataCastClient,
    private val kitsu: KitsuAnimeClient,
    private val imdbSuggest: ImdbSuggestClient,
    private val watchHistoryDao: WatchHistoryDao,
    private val watchlistDao: WatchlistDao,
    private val watchedDao: WatchedDao,
    private val profileRepository: ProfileRepository,
    private val secretVault: SecretVault
) {
    private val profileId: Long get() = profileRepository.currentProfileId()

    private val detailsMemoryCache = ConcurrentHashMap<String, MediaDetails>()
    private val cinemetaMetaCache = ConcurrentHashMap<String, CinemetaMetaDto>()

    fun hasTmdbCredentials(): Boolean = getTmdbApiKeyOrNull() != null

    fun saveTmdbApiKey(apiKey: String) {
        secretVault.putSecret(SecretVault.KEY_TMDB_API_KEY, apiKey.trim())
        detailsMemoryCache.clear()
    }

    fun clearTmdbApiKey() {
        secretVault.removeSecret(SecretVault.KEY_TMDB_API_KEY)
        detailsMemoryCache.clear()
    }

    /** Performs a lightweight request to confirm the key is accepted by TMDB. */
    suspend fun validateTmdbApiKey(apiKey: String): NetworkResult<Unit> = safeApiCall {
        tmdbApi.getPopularMovies(apiKey.trim())
        Unit
    }

    fun getTmdbApiKeyOrNull(): String? =
        secretVault.getSecret(SecretVault.KEY_TMDB_API_KEY)?.takeIf { it.isNotBlank() }

    fun observeContinueWatching(): Flow<List<WatchProgressItem>> {
        return profileRepository.activeProfileId.flatMapLatest { watchHistoryDao.observeContinueWatching(it) }.map { entities ->
            entities.map { entity ->
                WatchProgressItem(
                    mediaItem = MediaItem(
                        id = entity.mediaId,
                        tmdbId = entity.tmdbId,
                        imdbId = entity.imdbId,
                        title = entity.title,
                        type = entity.mediaType,
                        posterUrl = entity.posterUrl,
                        backdropUrl = entity.backdropUrl,
                        overview = "",
                        releaseYear = null,
                        rating = null
                    ),
                    seasonNumber = entity.seasonNumber,
                    episodeNumber = entity.episodeNumber,
                    episodeTitle = entity.episodeTitle,
                    positionMs = entity.positionMs,
                    durationMs = entity.durationMs,
                    updatedAtMs = entity.updatedAtMs
                )
            }
        }
    }

    fun observeWatchlist(): Flow<List<WatchlistEntity>> =
        profileRepository.activeProfileId.flatMapLatest { watchlistDao.observeWatchlist(it) }

    fun observeWatchlistItems(): Flow<List<MediaItem>> = observeWatchlist().map { rows ->
        rows.map {
            MediaItem(
                id = it.mediaId, tmdbId = it.tmdbId, imdbId = it.imdbId, title = it.title, type = it.mediaType,
                posterUrl = it.posterUrl, backdropUrl = it.backdropUrl, overview = "", releaseYear = it.releaseYear, rating = it.rating
            )
        }
    }

    fun observeFavorites(): Flow<List<WatchlistEntity>> =
        profileRepository.activeProfileId.flatMapLatest { watchlistDao.observeFavorites(it) }

    fun observeWatchlistEntry(mediaId: String): Flow<WatchlistEntity?> =
        profileRepository.activeProfileId.flatMapLatest { watchlistDao.observeItem(it, mediaId) }

    /** Watchlist ("saved") and favorite are independent; a row is removed only when both are off. */
    suspend fun toggleWatchlist(item: MediaItem) = updateSavedFlags(item) { saved, fav -> !saved to fav }

    suspend fun toggleFavorite(item: MediaItem) = updateSavedFlags(item) { saved, fav -> saved to !fav }

    private suspend fun updateSavedFlags(item: MediaItem, change: (Boolean, Boolean) -> Pair<Boolean, Boolean>) {
        val pid = profileId
        val existing = watchlistDao.getItem(pid, item.id)
        val (saved, favorite) = change(existing?.isInWatchlist == true, existing?.isFavorite == true)
        if (!saved && !favorite) {
            watchlistDao.remove(pid, item.id)
            return
        }
        watchlistDao.upsert(
            WatchlistEntity(
                profileId = pid,
                mediaId = item.id,
                tmdbId = item.tmdbId,
                imdbId = item.imdbId,
                title = item.title,
                mediaType = item.type,
                posterUrl = item.posterUrl,
                backdropUrl = item.backdropUrl,
                releaseYear = item.releaseYear,
                rating = item.rating,
                isInWatchlist = saved,
                isFavorite = favorite,
                addedAtMs = existing?.addedAtMs ?: System.currentTimeMillis()
            )
        )
    }

    /** Watched marks for a title as (season, episode) pairs; movies use (0, 0). */
    fun observeWatched(mediaId: String): Flow<Set<Pair<Int, Int>>> =
        profileRepository.activeProfileId
            .flatMapLatest { watchedDao.observeForMedia(it, mediaId) }
            .map { rows -> rows.map { it.seasonNumber to it.episodeNumber }.toSet() }

    suspend fun setWatched(mediaId: String, season: Int, episode: Int, watched: Boolean) {
        if (watched) {
            watchedDao.upsert(WatchedEntity(profileId, mediaId, season, episode, System.currentTimeMillis()))
        } else {
            watchedDao.remove(profileId, mediaId, season, episode)
        }
    }

    suspend fun loadHomeCatalogs(filterType: MediaType? = null): NetworkResult<List<CatalogRow>> {
        val apiKey = getTmdbApiKeyOrNull() ?: return loadCinemetaRows(filterType)

        return safeApiCall {
            coroutineScope {
                val trendingDeferred = async { if (filterType == null) tmdbApi.getTrendingAll(apiKey) else null }
                val popMoviesDeferred = async { if (filterType != MediaType.SERIES) tmdbApi.getPopularMovies(apiKey) else null }
                val popSeriesDeferred = async { if (filterType != MediaType.MOVIE) tmdbApi.getPopularSeries(apiKey) else null }
                val topMoviesDeferred = async { if (filterType != MediaType.SERIES) tmdbApi.getTopRatedMovies(apiKey) else null }
                val topSeriesDeferred = async { if (filterType != MediaType.MOVIE) tmdbApi.getTopRatedSeries(apiKey) else null }

                listOfNotNull(
                    trendingDeferred.await()?.let { res ->
                        CatalogRow("trending_all", "Trending This Week", null, res.results.mapNotNull { it.toDomain() }, CatalogQuery(CatalogKind.TRENDING, null))
                    },
                    popMoviesDeferred.await()?.let { res ->
                        CatalogRow("popular_movies", "Popular Movies", null, res.results.mapNotNull { it.toDomain(MediaType.MOVIE) }, CatalogQuery(CatalogKind.POPULAR, MediaType.MOVIE))
                    },
                    popSeriesDeferred.await()?.let { res ->
                        CatalogRow("popular_series", "Popular Series", null, res.results.mapNotNull { it.toDomain(MediaType.SERIES) }, CatalogQuery(CatalogKind.POPULAR, MediaType.SERIES))
                    },
                    topMoviesDeferred.await()?.let { res ->
                        CatalogRow("top_movies", "Top Rated Movies", null, res.results.mapNotNull { it.toDomain(MediaType.MOVIE) }, CatalogQuery(CatalogKind.TOP_RATED, MediaType.MOVIE))
                    },
                    topSeriesDeferred.await()?.let { res ->
                        CatalogRow("top_series", "Top Rated Series", null, res.results.mapNotNull { it.toDomain(MediaType.SERIES) }, CatalogQuery(CatalogKind.TOP_RATED, MediaType.SERIES))
                    }
                ).filter { it.items.isNotEmpty() }
            }
        }
    }

    private suspend fun loadCinemetaRows(filterType: MediaType?): NetworkResult<List<CatalogRow>> = coroutineScope {
        data class RowSpec(val id: String, val title: String, val type: MediaType, val catalogId: String)

        val specs = listOf(
            RowSpec("popular_movies", "Popular Movies", MediaType.MOVIE, "top"),
            RowSpec("popular_series", "Popular Series", MediaType.SERIES, "top"),
            RowSpec("featured_movies", "Featured Movies", MediaType.MOVIE, "imdbRating"),
            RowSpec("featured_series", "Featured Series", MediaType.SERIES, "imdbRating")
        ).filter { filterType == null || it.type == filterType }

        val results = specs.map { spec ->
            async { spec to cinemeta.fetchCatalog(spec.type.toStremioType(), spec.catalogId) }
        }.map { it.await() }

        val rows = results.mapNotNull { (spec, res) ->
            (res as? NetworkResult.Success)?.data
                ?.mapNotNull { it.toDomain() }
                ?.distinctBy { it.id }
                ?.let { withMissingRatings(it) }
                ?.takeIf { it.isNotEmpty() }
                ?.let {
                    val kind = if (spec.catalogId == "top") CatalogKind.POPULAR else CatalogKind.TOP_RATED
                    CatalogRow(spec.id, spec.title, null, it, CatalogQuery(kind, spec.type))
                }
        }

        if (rows.isEmpty()) {
            results.firstNotNullOfOrNull { it.second as? NetworkResult.Error }
                ?: NetworkResult.Error(
                    userMessage = "No catalogs available right now.",
                    diagnosticReason = "EMPTY_CATALOGS"
                )
        } else {
            NetworkResult.Success(rows)
        }
    }

    /** Home rows for the Anime filter (Kitsu catalogs, IMDb-linked titles only). */
    suspend fun loadAnimeRows(): NetworkResult<List<CatalogRow>> = coroutineScope {
        val results = KitsuAnimeClient.HOME_ROWS.map { (catalogId, title) ->
            async { Triple(catalogId, title, kitsu.fetchCatalog(catalogId)) }
        }.map { it.await() }
        val rows = results.mapNotNull { (catalogId, title, res) ->
            (res as? NetworkResult.Success)?.data?.takeIf { it.isNotEmpty() }?.let { items ->
                CatalogRow(
                    "anime_$catalogId", title, null, items,
                    // Kitsu's trending list has no paging; its "See all" opens the popular list instead.
                    CatalogQuery(CatalogKind.POPULAR, null, animeCatalog = catalogId.takeUnless { it.endsWith("trending") } ?: "kitsu-anime-popular")
                )
            }
        }
        if (rows.isEmpty()) {
            results.firstNotNullOfOrNull { it.third as? NetworkResult.Error }
                ?: NetworkResult.Error(userMessage = "Anime catalogs aren't available right now.", diagnosticReason = "EMPTY_ANIME")
        } else {
            NetworkResult.Success(rows)
        }
    }

    /**
     * Cinemeta's catalogs have no rating for some recent titles; fetch those from its live endpoint
     * (a handful of small, cached requests, run a few at a time).
     */
    private suspend fun withMissingRatings(items: List<MediaItem>, limit: Int = 24): List<MediaItem> = coroutineScope {
        val missing = items.filter { it.rating == null && it.id.startsWith("tt") }.take(limit)
        if (missing.isEmpty()) return@coroutineScope items
        val gate = Semaphore(6)
        val found = missing.map { item ->
            async {
                gate.withPermit {
                    item.id to cinemeta.fetchLiveMeta(item.type.toStremioType(), item.id)?.imdbRating?.toDoubleOrNull()
                }
            }
        }.map { it.await() }.toMap()
        items.map { item -> found[item.id]?.let { item.copy(rating = it) } ?: item }
    }

    /**
     * One page of a full catalog for the "See all" / genre grids.
     * [cursor] starts at 0; pass back [CatalogPage.nextCursor] to continue.
     */
    suspend fun loadCatalogPage(query: CatalogQuery, cursor: Int): NetworkResult<CatalogPage> {
        query.animeCatalog?.let { catalogId ->
            return when (val res = kitsu.fetchCatalog(catalogId, skip = cursor)) {
                is NetworkResult.Success -> NetworkResult.Success(
                    CatalogPage(items = res.data, nextCursor = if (res.data.isEmpty()) null else cursor + KITSU_PAGE_SIZE)
                )
                is NetworkResult.Error -> res
            }
        }
        val apiKey = getTmdbApiKeyOrNull()
        val type = query.type ?: MediaType.MOVIE

        // TMDB has no exact match for some genres (e.g. Horror or Biography for series); Cinemeta has them all.
        val tmdbCanServe = query.kind != CatalogKind.GENRE || query.genre?.let { CatalogGenres.tmdbGenreId(type, it) } != null
        if (apiKey != null && tmdbCanServe) {
            val page = cursor + 1
            return safeApiCall {
                val response = when (query.kind) {
                    CatalogKind.TRENDING -> tmdbApi.getTrendingAll(apiKey, page)
                    CatalogKind.POPULAR -> if (type == MediaType.MOVIE) tmdbApi.getPopularMovies(apiKey, page) else tmdbApi.getPopularSeries(apiKey, page)
                    CatalogKind.TOP_RATED -> if (type == MediaType.MOVIE) tmdbApi.getTopRatedMovies(apiKey, page) else tmdbApi.getTopRatedSeries(apiKey, page)
                    CatalogKind.GENRE -> {
                        val genreId = query.genre?.let { CatalogGenres.tmdbGenreId(type, it) }
                            ?: throw IllegalArgumentException("Genre not available for this type")
                        if (type == MediaType.MOVIE) tmdbApi.discoverMovies(apiKey, genreId.toString(), page)
                        else tmdbApi.discoverSeries(apiKey, genreId.toString(), page)
                    }
                }
                val forced = if (query.kind == CatalogKind.TRENDING && query.type == null) null else type
                CatalogPage(
                    items = response.results.mapNotNull { it.toDomain(forced) },
                    nextCursor = if (page < response.totalPages && response.results.isNotEmpty()) cursor + 1 else null
                )
            }
        }

        val catalogId = if (query.kind == CatalogKind.TOP_RATED) "imdbRating" else "top"
        val genre = query.genre.takeIf { query.kind == CatalogKind.GENRE }
        return when (val res = cinemeta.fetchCatalog(type.toStremioType(), catalogId, genre = genre, skip = cursor)) {
            is NetworkResult.Success -> NetworkResult.Success(
                CatalogPage(
                    items = withMissingRatings(res.data.mapNotNull { it.toDomain(type) }),
                    nextCursor = if (res.data.isEmpty()) null else cursor + res.data.size
                )
            )
            is NetworkResult.Error -> res
        }
    }

    /** Drops a title from Continue Watching (the player stores one entry per movie / series). */
    suspend fun removeFromContinueWatching(item: MediaItem) {
        watchHistoryDao.removeWatchProgress(profileId, "${item.type.name.lowercase()}:${item.id}")
    }

    /**
     * Instant search via IMDb's type-ahead (~0.5s, up to ~8 best matches). The search screen shows these
     * first, then merges the fuller [searchCatalog] results, which can take several seconds on Cinemeta.
     */
    suspend fun quickSearch(query: String, filterType: MediaType?): List<MediaItem> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        val items = (imdbSuggest.search(trimmed) as? NetworkResult.Success)?.data.orEmpty()
            .filter { filterType == null || it.type == filterType }
        return withMissingRatings(items, limit = 12)
    }

    suspend fun searchCatalog(
        query: String,
        filterType: MediaType?
    ): NetworkResult<List<MediaItem>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return NetworkResult.Success(emptyList())

        val apiKey = getTmdbApiKeyOrNull()
        if (apiKey != null) {
            return safeApiCall {
                tmdbApi.searchMulti(apiKey = apiKey, query = trimmed)
                    .results
                    .mapNotNull { it.toDomain() }
                    .filter { filterType == null || it.type == filterType }
            }
        }

        return coroutineScope {
            val types = listOfNotNull(
                MediaType.MOVIE.takeIf { filterType != MediaType.SERIES },
                MediaType.SERIES.takeIf { filterType != MediaType.MOVIE }
            )
            val results = types.map { type ->
                async { cinemeta.fetchCatalog(type.toStremioType(), "top", search = trimmed) }
            }.map { it.await() }

            val items = results.flatMap { res ->
                (res as? NetworkResult.Success)?.data?.mapNotNull { it.toDomain() }.orEmpty()
            }.distinctBy { "${it.type}_${it.id}" }

            if (items.isEmpty() && results.all { it is NetworkResult.Error }) {
                results.first() as NetworkResult.Error
            } else {
                NetworkResult.Success(items)
            }
        }
    }

    suspend fun getMediaDetails(
        mediaType: MediaType,
        mediaId: String
    ): NetworkResult<MediaDetails> {
        val cacheKey = "${mediaType.name}:$mediaId"
        detailsMemoryCache[cacheKey]?.let { return NetworkResult.Success(it.withWatchlistState()) }

        val apiKey = getTmdbApiKeyOrNull()
        val numericTmdbId = mediaId.removePrefix("tmdb:").toIntOrNull()

        val result: NetworkResult<MediaDetails> = when {
            numericTmdbId != null && apiKey != null -> loadTmdbDetails(mediaType, numericTmdbId, apiKey)
            numericTmdbId != null -> NetworkResult.Error(
                userMessage = "This title came from TMDB. Add your TMDB API key in Settings to open it.",
                diagnosticReason = "MISSING_TMDB_KEY",
                isRetryable = false
            )
            else -> loadCinemetaDetails(mediaType, mediaId)
        }

        if (result is NetworkResult.Success) {
            detailsMemoryCache[cacheKey] = result.data
            return NetworkResult.Success(result.data.withWatchlistState())
        }
        return result
    }

    private suspend fun MediaDetails.withWatchlistState(): MediaDetails {
        val wl = watchlistDao.getItem(profileId, item.id)
        return copy(isInWatchlist = wl?.isInWatchlist == true, isFavorite = wl?.isFavorite == true)
    }

    private suspend fun loadTmdbDetails(
        mediaType: MediaType,
        tmdbId: Int,
        apiKey: String
    ): NetworkResult<MediaDetails> = safeApiCall {
        val dto = if (mediaType == MediaType.MOVIE) {
            tmdbApi.getMovieDetails(movieId = tmdbId, apiKey = apiKey)
        } else {
            tmdbApi.getSeriesDetails(tvId = tmdbId, apiKey = apiKey)
        }
        dto.toDomainDetails(mediaType)
    }

    private suspend fun loadCinemetaDetails(
        mediaType: MediaType,
        imdbId: String
    ): NetworkResult<MediaDetails> {
        return when (val res = fetchCinemetaMeta(mediaType, imdbId)) {
            is NetworkResult.Success -> {
                val meta = res.data
                val item = meta.toDomain(forcedType = mediaType)
                    ?: return NetworkResult.Error(
                        userMessage = "Metadata for this title is incomplete.",
                        diagnosticReason = "CINEMETA_INVALID_META",
                        isRetryable = false
                    )
                val seasons = meta.videos.orEmpty()
                    .mapNotNull { video -> video.season?.takeIf { it > 0 }?.let { it to video } }
                    .groupBy({ it.first }, { it.second })
                    .toSortedMap()
                    .map { (season, eps) ->
                        SeasonSummary(
                            seasonNumber = season,
                            name = "Season $season",
                            episodeCount = eps.size,
                            posterUrl = null,
                            airDate = eps.firstNotNullOfOrNull { it.released ?: it.firstAired }?.take(10)
                        )
                    }
                val tmdbExtras = loadTmdbExtrasFor(mediaType, meta.tmdbId)
                val liveRating = if (item.rating == null) {
                    cinemeta.fetchLiveMeta(mediaType.toStremioType(), imdbId)?.imdbRating?.toDoubleOrNull()
                } else null
                val cast = tmdbExtras?.cast?.takeIf { it.isNotEmpty() }
                    ?: keylessCast(mediaType, imdbId, meta.cast.orEmpty())
                NetworkResult.Success(
                    MediaDetails(
                        item = if (liveRating != null) item.copy(rating = liveRating) else item,
                        runtimeMinutes = meta.runtime?.filter { it.isDigit() }?.toIntOrNull(),
                        status = meta.status,
                        tagline = tmdbExtras?.tagline,
                        cast = cast,
                        seasons = seasons,
                        trailerYoutubeId = tmdbExtras?.trailerYoutubeId
                            ?: meta.trailers.orEmpty().firstOrNull { it.type.equals("Trailer", true) && !it.source.isNullOrBlank() }?.source
                            ?: meta.trailers.orEmpty().firstOrNull { !it.source.isNullOrBlank() }?.source
                    )
                )
            }
            is NetworkResult.Error -> res
        }
    }

    /** Full cast with photos (and tagline) from TMDB when a key is set and Cinemeta knows the TMDB id. */
    private suspend fun loadTmdbExtrasFor(mediaType: MediaType, tmdbId: Int?): MediaDetails? {
        val apiKey = getTmdbApiKeyOrNull() ?: return null
        if (tmdbId == null) return null
        return (loadTmdbDetails(mediaType, tmdbId, apiKey) as? NetworkResult.Success)?.data
    }

    /**
     * Without TMDB: the billed cast with portraits from Cinemeta's live endpoint, then everyone else
     * Wikidata lists (with Commons photos). Falls back to Cinemeta's few names + Wikipedia photos.
     */
    private suspend fun keylessCast(mediaType: MediaType, imdbId: String, fallbackNames: List<String>): List<CastMember> = coroutineScope {
        val billedDeferred = async { cinemeta.fetchCredits(mediaType.toStremioType(), imdbId) }
        val wikidataDeferred = async { wikidataCast.castFor(imdbId) }
        val billed = billedDeferred.await().map { c ->
            CastMember(
                id = c.id ?: c.name.hashCode(),
                name = c.name,
                character = c.character.orEmpty(),
                profileUrl = c.profilePath?.takeIf { it.isNotBlank() }?.let { "${TmdbApiService.IMAGE_W185}$it" }
            )
        }
        val known = billed.map { it.name.lowercase() }.toMutableSet()
        val extra = wikidataDeferred.await()
            .filter { known.add(it.name.lowercase()) }
            .sortedBy { it.photoUrl == null }
            .map { CastMember(id = it.name.hashCode(), name = it.name, character = it.character, profileUrl = it.photoUrl) }
        val merged = (billed + extra).take(MAX_CAST)
        merged.ifEmpty { castWithWikipediaPhotos(fallbackNames) }
    }

    private suspend fun castWithWikipediaPhotos(names: List<String>): List<CastMember> = coroutineScope {
        names.take(15).mapIndexed { idx, name ->
            async { CastMember(id = idx, name = name, character = "", profileUrl = wikipediaPhotos.actorPhotoUrl(name)) }
        }.map { it.await() }
    }

    private suspend fun fetchCinemetaMeta(mediaType: MediaType, imdbId: String): NetworkResult<CinemetaMetaDto> {
        val key = "${mediaType.name}:$imdbId"
        cinemetaMetaCache[key]?.let { return NetworkResult.Success(it) }
        val res = cinemeta.fetchMeta(mediaType.toStremioType(), imdbId)
        if (res is NetworkResult.Success) cinemetaMetaCache[key] = res.data
        return res
    }

    suspend fun getSeasonEpisodes(
        mediaId: String,
        seasonNumber: Int
    ): NetworkResult<List<EpisodeItem>> {
        val apiKey = getTmdbApiKeyOrNull()
        val numericTmdbId = mediaId.toIntOrNull()

        if (numericTmdbId != null && apiKey != null) {
            return safeApiCall {
                tmdbApi.getSeasonEpisodes(
                    tvId = numericTmdbId,
                    seasonNumber = seasonNumber,
                    apiKey = apiKey
                ).episodes.map { ep ->
                    EpisodeItem(
                        id = ep.id,
                        seasonNumber = ep.seasonNumber,
                        episodeNumber = ep.episodeNumber,
                        title = ep.name,
                        overview = ep.overview,
                        stillUrl = ep.stillPath?.let { "${TmdbApiService.IMAGE_W780}$it" },
                        airDate = ep.airDate,
                        runtimeMinutes = ep.runtime,
                        rating = ep.voteAverage
                    )
                }
            }
        }

        return when (val res = fetchCinemetaMeta(MediaType.SERIES, mediaId)) {
            is NetworkResult.Success -> NetworkResult.Success(
                res.data.videos.orEmpty()
                    .filter { it.season == seasonNumber }
                    .sortedBy { it.episode ?: it.number ?: 0 }
                    .map { video ->
                        val episodeNumber = video.episode ?: video.number ?: 0
                        EpisodeItem(
                            id = seasonNumber * 10_000 + episodeNumber,
                            seasonNumber = seasonNumber,
                            episodeNumber = episodeNumber,
                            title = video.name ?: video.title ?: "Episode $episodeNumber",
                            overview = video.overview ?: video.description.orEmpty(),
                            stillUrl = video.thumbnail,
                            airDate = (video.released ?: video.firstAired)?.take(10),
                            runtimeMinutes = null,
                            rating = video.rating?.toDoubleOrNull()
                        )
                    }
                    .distinctBy { it.episodeNumber }
            )
            is NetworkResult.Error -> res
        }
    }

    private fun MediaType.toStremioType(): String = if (this == MediaType.MOVIE) "movie" else "series"

    private fun CinemetaMetaDto.toDomain(forcedType: MediaType? = null): MediaItem? {
        val resolvedType = forcedType ?: when (type) {
            "movie" -> MediaType.MOVIE
            "series" -> MediaType.SERIES
            else -> return null
        }
        val imdb = imdbId ?: id.takeIf { it.startsWith("tt") } ?: return null
        if (name.isBlank()) return null
        return MediaItem(
            id = imdb,
            tmdbId = tmdbId,
            imdbId = imdb,
            title = name,
            type = resolvedType,
            posterUrl = CinemetaClient.upgradePosterUrl(poster),
            backdropUrl = background,
            overview = description.orEmpty(),
            releaseYear = (releaseInfo ?: year)?.take(4),
            rating = imdbRating?.toDoubleOrNull(),
            genres = (genres ?: genre).orEmpty()
        )
    }

    private fun TmdbMediaDto.toDomain(forcedType: MediaType? = null): MediaItem? {
        val resolvedType = forcedType ?: when (mediaType) {
            "movie" -> MediaType.MOVIE
            "tv" -> MediaType.SERIES
            // search/multi also returns "person" results, which are not playable titles
            else -> return null
        }
        val displayTitle = title ?: name ?: return null
        val rawDate = releaseDate ?: firstAirDate
        return MediaItem(
            id = id.toString(),
            tmdbId = id,
            imdbId = null, // Resolved via external_ids on the details screen
            title = displayTitle,
            type = resolvedType,
            posterUrl = posterPath?.let { "${TmdbApiService.IMAGE_W500}$it" },
            backdropUrl = backdropPath?.let { "${TmdbApiService.IMAGE_W1280}$it" },
            overview = overview,
            releaseYear = rawDate?.takeIf { it.isNotBlank() }?.take(4),
            rating = voteAverage,
            genres = genreIds.mapNotNull { GENRE_MAP[it] }
        )
    }

    private fun TmdbDetailsDto.toDomainDetails(type: MediaType): MediaDetails {
        val resolvedImdbId = imdbId ?: externalIds?.imdbId
        val displayTitle = title ?: name ?: "Untitled"
        val rawDate = releaseDate ?: firstAirDate
        val mediaItem = MediaItem(
            id = id.toString(),
            tmdbId = id,
            imdbId = resolvedImdbId?.takeIf { it.isNotBlank() },
            title = displayTitle,
            type = type,
            posterUrl = posterPath?.let { "${TmdbApiService.IMAGE_W500}$it" },
            backdropUrl = backdropPath?.let { "${TmdbApiService.IMAGE_W1280}$it" },
            overview = overview,
            releaseYear = rawDate?.takeIf { it.isNotBlank() }?.take(4),
            rating = voteAverage,
            genres = genres.map { it.name }
        )
        return MediaDetails(
            item = mediaItem,
            runtimeMinutes = runtime ?: episodeRunTime.firstOrNull(),
            status = status,
            tagline = tagline?.takeIf { it.isNotBlank() },
            cast = credits?.cast.orEmpty().take(MAX_CAST).map { c ->
                CastMember(
                    id = c.id,
                    name = c.name,
                    character = c.character,
                    profileUrl = c.profilePath?.let { "${TmdbApiService.IMAGE_W185}$it" }
                )
            },
            seasons = seasons
                .filter { it.seasonNumber > 0 }
                .sortedBy { it.seasonNumber }
                .map { s ->
                    SeasonSummary(
                        seasonNumber = s.seasonNumber,
                        name = s.name,
                        episodeCount = s.episodeCount,
                        posterUrl = s.posterPath?.let { "${TmdbApiService.IMAGE_W500}$it" },
                        airDate = s.airDate
                    )
                },
            trailerYoutubeId = videos?.results.orEmpty()
                .filter { it.site.equals("YouTube", true) && it.key.isNotBlank() }
                .sortedWith(compareByDescending<com.argon.stream.data.remote.tmdb.TmdbVideoDto> { it.type == "Trailer" }.thenByDescending { it.official })
                .firstOrNull()?.key
        )
    }

    companion object {
        private const val MAX_CAST = 40
        private const val KITSU_PAGE_SIZE = 20
        private val GENRE_MAP = mapOf(
            28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy",
            80 to "Crime", 99 to "Documentary", 18 to "Drama", 10751 to "Family",
            14 to "Fantasy", 36 to "History", 27 to "Horror", 10402 to "Music",
            9648 to "Mystery", 10749 to "Romance", 878 to "Sci-Fi", 53 to "Thriller",
            10752 to "War", 37 to "Western", 10759 to "Action & Adventure",
            10762 to "Kids", 10765 to "Sci-Fi & Fantasy", 10768 to "War & Politics"
        )
    }
}
