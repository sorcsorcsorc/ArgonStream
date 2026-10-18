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
 * Genres offered for browsing. Only genres that both Cinemeta and TMDB can filter on are listed,
 * so a genre never silently returns nothing after a TMDB key is added or removed.
 */
object CatalogGenres {
    val MOVIE = listOf(
        "Action", "Adventure", "Animation", "Comedy", "Crime", "Documentary", "Drama", "Family", "Fantasy",
        "History", "Horror", "Mystery", "Romance", "Sci-Fi", "Thriller", "War", "Western"
    )
    val SERIES = listOf(
        "Action", "Animation", "Comedy", "Crime", "Documentary", "Drama", "Family", "Mystery",
        "Reality-TV", "Sci-Fi", "War", "Western"
    )

    fun forType(type: MediaType): List<String> = if (type == MediaType.MOVIE) MOVIE else SERIES

    fun tmdbGenreId(type: MediaType, genre: String): Int? =
        if (type == MediaType.MOVIE) TMDB_MOVIE[genre] else TMDB_TV[genre]

    private val TMDB_MOVIE = mapOf(
        "Action" to 28, "Adventure" to 12, "Animation" to 16, "Comedy" to 35, "Crime" to 80, "Documentary" to 99,
        "Drama" to 18, "Family" to 10751, "Fantasy" to 14, "History" to 36, "Horror" to 27, "Mystery" to 9648,
        "Romance" to 10749, "Sci-Fi" to 878, "Thriller" to 53, "War" to 10752, "Western" to 37
    )
    private val TMDB_TV = mapOf(
        "Action" to 10759, "Animation" to 16, "Comedy" to 35, "Crime" to 80, "Documentary" to 99, "Drama" to 18,
        "Family" to 10751, "Mystery" to 9648, "Reality-TV" to 10764, "Sci-Fi" to 10765, "War" to 10768, "Western" to 37
    )
}
