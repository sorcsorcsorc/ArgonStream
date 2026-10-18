package com.argon.stream.domain.model

import kotlinx.serialization.Serializable

/** What a full-screen catalog grid shows ("See all" on a Home row, or a genre). */
@Serializable
enum class CatalogKind { TRENDING, POPULAR, TOP_RATED, GENRE }

@Serializable
data class CatalogQuery(
    val kind: CatalogKind,
    /** null = movies and series mixed (only meaningful for TRENDING with TMDB). */
    val type: MediaType?,
    val genre: String? = null
)

/** One page of a catalog; [nextCursor] is null when there is nothing more to load. */
data class CatalogPage(
    val items: List<MediaItem>,
    val nextCursor: Int?
)

/**
 * Genres offered for browsing: every genre Cinemeta can filter on. TMDB is used for a genre only when
 * it has a matching id; otherwise the grid falls back to Cinemeta, so no genre comes back empty.
 */
object CatalogGenres {
    val MOVIE = listOf(
        "Action", "Adventure", "Animation", "Biography", "Comedy", "Crime", "Documentary", "Drama", "Family",
        "Fantasy", "History", "Horror", "Mystery", "Romance", "Sci-Fi", "Sport", "Thriller", "War", "Western"
    )
    val SERIES = MOVIE + listOf("Reality-TV", "Talk-Show", "Game-Show")

    fun forType(type: MediaType): List<String> = if (type == MediaType.MOVIE) MOVIE else SERIES

    fun tmdbGenreId(type: MediaType, genre: String): Int? =
        if (type == MediaType.MOVIE) TMDB_MOVIE[genre] else TMDB_TV[genre]

    private val TMDB_MOVIE = mapOf(
        "Action" to 28, "Adventure" to 12, "Animation" to 16, "Comedy" to 35, "Crime" to 80, "Documentary" to 99,
        "Drama" to 18, "Family" to 10751, "Fantasy" to 14, "History" to 36, "Horror" to 27, "Mystery" to 9648,
        "Romance" to 10749, "Sci-Fi" to 878, "Thriller" to 53, "War" to 10752, "Western" to 37
    )
    private val TMDB_TV = mapOf(
        "Animation" to 16, "Comedy" to 35, "Crime" to 80, "Documentary" to 99, "Drama" to 18,
        "Family" to 10751, "Mystery" to 9648, "Reality-TV" to 10764, "Talk-Show" to 10767, "Western" to 37
    )
}
