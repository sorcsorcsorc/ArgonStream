package com.argon.stream.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.argon.stream.domain.model.ArgonPreferences
import com.argon.stream.domain.model.CastMember
import com.argon.stream.domain.model.CatalogRow
import com.argon.stream.domain.model.EpisodeItem
import com.argon.stream.domain.model.ExternalPlayerTarget
import com.argon.stream.domain.model.InstalledAddon
import com.argon.stream.domain.model.MediaDetails
import com.argon.stream.domain.model.MediaItem
import com.argon.stream.domain.model.MediaType
import com.argon.stream.domain.model.SeasonSummary
import com.argon.stream.domain.model.StreamCandidate
import com.argon.stream.domain.model.TorBoxAccountInfo
import com.argon.stream.domain.model.TorBoxPlanTier
import com.argon.stream.domain.model.WatchProgressItem
import com.argon.stream.ui.screens.addons.AddonManagerActions
import com.argon.stream.ui.screens.addons.AddonManagerContent
import com.argon.stream.ui.screens.addons.AddonManagerUiState
import com.argon.stream.core.download.DownloadItem
import com.argon.stream.core.download.DownloadStatus
import com.argon.stream.ui.navigation.ArgonBottomBar
import com.argon.stream.ui.navigation.TopLevelDestination
import com.argon.stream.ui.screens.details.DetailsContent
import com.argon.stream.ui.screens.downloads.DownloadsContent
import com.argon.stream.ui.screens.downloads.DownloadsUiState
import com.argon.stream.ui.screens.details.TitleDetailsUiState
import com.argon.stream.ui.screens.home.HomeContent
import com.argon.stream.ui.screens.home.HomeUiState
import com.argon.stream.ui.screens.library.LibraryContent
import com.argon.stream.ui.screens.library.LibraryUiState
import com.argon.stream.ui.screens.player.FitMode
import com.argon.stream.ui.screens.player.PlayerControlsOverlay
import com.argon.stream.ui.screens.player.PlayerUiState
import com.argon.stream.ui.screens.search.SearchContent
import com.argon.stream.ui.screens.search.SearchUiState
import com.argon.stream.ui.screens.settings.SettingsActions
import com.argon.stream.ui.screens.settings.SettingsContent
import com.argon.stream.ui.screens.settings.SettingsUiState
import com.argon.stream.ui.screens.streams.StreamSelectionContent
import com.argon.stream.ui.screens.streams.StreamSelectionUiState
import com.argon.stream.ui.theme.ArgonTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders each screen with sample data at real phone sizes and writes PNGs to app/build/screenshots,
 * so layouts can be reviewed without an emulator or device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = PHONE)
class ScreenLayoutScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent {
            ArgonTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    content()
                }
            }
        }
        composeRule.waitForIdle()
        val view = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun home() = capture("01_home_phone") { HomeScreenSample() }

    @Test
    @Config(qualifiers = SMALL_PHONE)
    fun homeSmallPhone() = capture("01b_home_small_phone") { HomeScreenSample() }

    @Composable
    private fun HomeScreenSample() {
        HomeContent(
            state = HomeUiState(
                isLoading = false,
                featuredItem = sampleMovies.first(),
                continueWatching = listOf(
                    WatchProgressItem(sampleSeries.first(), 2, 5, "S2E5", 1_200_000, 2_700_000, 0)
                ),
                catalogRows = listOf(
                    CatalogRow("popular_movies", "Popular Movies", null, sampleMovies),
                    CatalogRow("popular_series", "Popular Series", null, sampleSeries)
                ),
                isTmdbConfigured = false
            ),
            onOpenMediaDetails = {}, onOpenSettings = {}, onOpenSearch = {}, onSetFilter = {}, onRetry = {}
        )
    }

    @Test
    fun search() = capture("02_search_phone") {
        SearchContent(
            state = SearchUiState(query = "star", results = sampleMovies + sampleSeries),
            onQueryChange = {}, onTypeFilterChange = {}, onMinRatingChange = {}, onSortOrderChange = {}, onOpenMediaDetails = {}
        )
    }

    @Test
    fun library() = capture("03_library_phone") {
        LibraryContent(
            state = LibraryUiState(watchlist = sampleMovies.take(3), favorites = emptyList(), history = emptyList()),
            onOpenMediaDetails = {}
        )
    }

    @Test
    fun detailsSeries() = capture("04_details_series_phone") {
        val details = MediaDetails(
            item = sampleSeries.first(),
            runtimeMinutes = 49,
            status = "Ended",
            tagline = null,
            cast = listOf("Bryan Cranston", "Aaron Paul", "Anna Gunn", "Dean Norris", "Betsy Brandt")
                .mapIndexed { i, n -> CastMember(i, n, "", null) },
            seasons = (1..5).map { SeasonSummary(it, "Season $it", 13, null, "2008-01-20") }
        )
        DetailsContent(
            details = details,
            state = TitleDetailsUiState(
                isLoading = false,
                details = details,
                episodes = (1..4).map {
                    EpisodeItem(it, 1, it, "Episode title number $it", "A long overview of what happens in this episode that spans a couple of lines on a phone.", null, "2008-01-2$it", 48, 8.1)
                }
            ),
            onBack = {}, onSelectStreams = { _, _ -> }, onToggleWatchlist = {}, onToggleFavorite = {}, onSelectSeason = {}
        )
    }

    @Test
    fun detailsMovie() = capture("05_details_movie_phone") {
        val details = MediaDetails(
            item = sampleMovies[1],
            runtimeMinutes = 136,
            status = "Released",
            tagline = "Welcome to the Real World.",
            cast = emptyList(),
            seasons = emptyList()
        )
        DetailsContent(
            details = details,
            state = TitleDetailsUiState(isLoading = false, details = details, isInWatchlist = true),
            onBack = {}, onSelectStreams = { _, _ -> }, onToggleWatchlist = {}, onToggleFavorite = {}, onSelectSeason = {}
        )
    }

    @Test
    fun streams() = capture("06_streams_phone") {
        StreamSelectionContent(
            state = StreamSelectionUiState(
                title = "Breaking Bad S1E1",
                isLoading = false,
                availableResolutions = listOf("2160p", "1080p", "720p"),
                streams = sampleStreams,
                hiddenByQualityLimit = 2,
                maxResolution = "1080p",
                providerWarnings = listOf("Torrentio: Connection timed out.")
            ),
            onBack = {}, onOpenAddons = {}, onOpenSettings = {}, onRefresh = {}, onSelectFilter = {},
            onSelectStream = {}, onDismissStatus = {}, onDismissMultiFile = {}, onSelectMultiFile = { _, _ -> }
        )
    }

    @Test
    fun streamsNoAddons() = capture("06b_streams_no_addons_phone") {
        StreamSelectionContent(
            state = StreamSelectionUiState(title = "The Matrix", isLoading = false, hasStreamAddons = false),
            onBack = {}, onOpenAddons = {}, onOpenSettings = {}, onRefresh = {}, onSelectFilter = {},
            onSelectStream = {}, onDismissStatus = {}, onDismissMultiFile = {}, onSelectMultiFile = { _, _ -> }
        )
    }

    @Test
    fun addons() = capture("07_addons_phone") {
        AddonManagerContent(
            state = AddonManagerUiState(
                addons = listOf(
                    InstalledAddon("com.linvo.cinemeta", "Cinemeta", "3.0.14", null, "https://v3-cinemeta.strem.io/manifest.json",
                        "https://v3-cinemeta.strem.io", listOf("catalog", "meta"), listOf("movie", "series"), listOf("tt"), true, 0, "VERIFIED"),
                    InstalledAddon("aiostreams", "AIOStreams", "2.4.0", null, "https://example.invalid/manifest.json",
                        "https://example.invalid", listOf("stream", "subtitles"), listOf("movie", "series"), listOf("tt"), true, 1, "ONLINE • v2.4.0")
                ),
                statusMessage = "Installed AIOStreams (v2.4.0)"
            ),
            actions = object : AddonManagerActions {}
        )
    }

    @Test
    fun settings() = capture("08_settings_phone") {
        SettingsContent(
            state = SettingsUiState(
                preferences = ArgonPreferences(preferredMaxResolution = "1080p"),
                isTorBoxSaved = true,
                torBoxAccount = TorBoxAccountInfo(1, "viewer@example.com", TorBoxPlanTier.PRO, true, null, 0),
                availablePlayers = listOf(ExternalPlayerTarget.INTERNAL_EXOPLAYER, ExternalPlayerTarget.VLC, ExternalPlayerTarget.SYSTEM_CHOOSER)
            ),
            actions = object : SettingsActions {}
        )
    }

    @Test
    fun downloads() = capture("10_downloads_phone") {
        DownloadsContent(
            state = DownloadsUiState(
                isLoading = false,
                items = listOf(
                    DownloadItem(3, "Breaking.Bad.S01E02.1080p.BluRay.x265.mkv", DownloadStatus.RUNNING, 620_000_000, 1_450_000_000),
                    DownloadItem(2, "The.Matrix.1999.1080p.BluRay.mkv", DownloadStatus.COMPLETED, 2_100_000_000, 2_100_000_000),
                    DownloadItem(1, "Inception.2010.720p.mkv", DownloadStatus.FAILED, 10_000_000, 900_000_000)
                )
            ),
            onPlay = {}, onRemove = {}
        )
    }

    @Test
    @Config(qualifiers = SMALL_PHONE)
    fun bottomBarSmallPhone() = capture("11_bottom_bar_small_phone") {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                ArgonBottomBar(isSelected = { it == TopLevelDestination.DOWNLOADS }, onNavigate = {})
            }
        }
    }

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun playerControlsLandscape() = capture("09_player_landscape") {
        Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray)) {
            PlayerControlsOverlay(
                state = PlayerUiState(
                    title = "Breaking Bad",
                    subtitle = "Season 1 · Episode 1",
                    isPlaying = true,
                    isBuffering = false,
                    currentPositionMs = 1_250_000,
                    durationMs = 3_480_000,
                    playbackSpeed = 1.25f,
                    availableExternalPlayers = listOf(ExternalPlayerTarget.INTERNAL_EXOPLAYER, ExternalPlayerTarget.VLC)
                ),
                fitMode = FitMode.FIT,
                scrubFraction = null,
                onBack = {}, onTracks = {}, onSpeed = {}, onExternal = {}, onCycleFit = {}, onRotate = {},
                onTogglePlay = {}, onSeekBack = {}, onSeekForward = {}, onScrub = {}, onScrubFinished = {}, onRetry = {}
            )
        }
    }

    private companion object {
        val sampleMovies = listOf(
            MediaItem("tt22084616", null, "tt22084616", "Spider-Man: Brand New Day", MediaType.MOVIE, null, null,
                "A forgotten Peter Parker lives alone as a full-time Spider-Man until mounting pressure triggers a dangerous change.",
                "2026", 8.0, listOf("Action", "Adventure")),
            MediaItem("tt0133093", 603, "tt0133093", "The Matrix", MediaType.MOVIE, null, null,
                "A computer hacker learns about the true nature of reality and his role in the war against its controllers.",
                "1999", 8.7, listOf("Action", "Sci-Fi")),
            MediaItem("tt0816692", null, "tt0816692", "Interstellar", MediaType.MOVIE, null, null, "", "2014", 8.7, listOf("Sci-Fi")),
            MediaItem("tt1375666", null, "tt1375666", "Inception", MediaType.MOVIE, null, null, "", "2010", 8.8, listOf("Thriller")),
            MediaItem("tt0468569", null, "tt0468569", "The Dark Knight", MediaType.MOVIE, null, null, "", "2008", 9.0, listOf("Crime"))
        )
        val sampleSeries = listOf(
            MediaItem("tt0903747", 1396, "tt0903747", "Breaking Bad", MediaType.SERIES, null, null,
                "A chemistry teacher diagnosed with inoperable lung cancer turns to manufacturing methamphetamine.",
                "2008", 9.5, listOf("Crime", "Drama", "Thriller")),
            MediaItem("tt0944947", null, "tt0944947", "Game of Thrones", MediaType.SERIES, null, null, "", "2011", 9.2, listOf("Drama")),
            MediaItem("tt4574334", null, "tt4574334", "Stranger Things", MediaType.SERIES, null, null, "", "2016", 8.7, listOf("Horror"))
        )
        val sampleStreams = listOf(
            StreamCandidate("a", "aio", "AIOStreams | TorBox", "Breaking.Bad.S01E01.1080p.BluRay.x265.HEVC.10bit.AAC.5.1-Group.mkv",
                directUrl = "https://example.invalid/1", resolution = "1080p", codec = "HEVC", hdrTag = null, audioTag = "Surround",
                sizeDisplay = "1.4 GB", seeders = 152, languages = listOf("EN"), isCachedOnDebrid = true),
            StreamCandidate("b", "aio", "AIOStreams | TorBox", "Breaking.Bad.S01E01.2160p.WEB-DL.DV.HDR10.DDP5.1.mkv",
                directUrl = "https://example.invalid/2", resolution = "1080p", codec = "H.264", hdrTag = "HDR", audioTag = "EAC3",
                sizeDisplay = "6.2 GB", seeders = 40, isCachedOnDebrid = false),
            StreamCandidate("c", "torrentio", "Torrentio", "Breaking Bad S01E01 720p",
                infoHash = "0123456789abcdef0123456789abcdef01234567", resolution = "720p", codec = null, hdrTag = null, audioTag = null,
                sizeDisplay = "650 MB", seeders = 12)
        )
    }
}

private const val PHONE = "w393dp-h851dp-xxhdpi"
private const val SMALL_PHONE = "w360dp-h640dp-xhdpi"
private const val PHONE_LANDSCAPE = "w851dp-h393dp-land-xxhdpi"
