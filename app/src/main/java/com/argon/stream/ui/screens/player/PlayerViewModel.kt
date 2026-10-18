package com.argon.stream.ui.screens.player

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.argon.stream.core.player.ExternalPlayerLauncher
import com.argon.stream.core.player.PlaybackErrorClassifier
import com.argon.stream.core.player.PlaybackRequest
import com.argon.stream.core.player.PlaybackSessionStore
import com.argon.stream.core.player.SeekPreviewGenerator
import com.argon.stream.core.player.SkipSegments
import com.argon.stream.core.player.SkipSegmentsClient
import com.argon.stream.core.player.SubtitleLanguages
import com.argon.stream.data.local.UserPreferencesRepository
import com.argon.stream.data.local.WatchHistoryDao
import com.argon.stream.data.local.WatchHistoryEntity
import com.argon.stream.data.remote.addon.StremioSubtitleClient
import com.argon.stream.domain.model.ExternalPlayerTarget
import com.argon.stream.domain.model.ExternalSubtitleTrack
import com.argon.stream.domain.model.MediaType
import com.argon.stream.domain.model.PlaybackDiagnosticError
import com.argon.stream.domain.model.SelectableTrackInfo
import com.argon.stream.domain.model.SubtitleScalePreset
import com.argon.stream.core.network.NetworkResult
import com.argon.stream.data.local.StreamChoiceDao
import com.argon.stream.data.repository.AddonRepository
import com.argon.stream.data.repository.CatalogRepository
import com.argon.stream.data.repository.ProfileRepository
import com.argon.stream.data.repository.TorBoxRepository
import com.argon.stream.domain.model.EpisodeItem
import com.argon.stream.domain.model.TorBoxResolutionOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.LocalDate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.Locale
import javax.inject.Inject

data class PlayerUiState(
    val streamUrl: String = "",
    val title: String = "",
    val subtitle: String? = null,
    val stremioVideoId: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = true,
    val isEnded: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val videoAspectRatio: Float = 16f / 9f,
    val playbackSpeed: Float = 1.0f,
    val audioTracks: List<SelectableTrackInfo> = emptyList(),
    val subtitleTracks: List<SelectableTrackInfo> = emptyList(),
    val externalSubtitles: List<ExternalSubtitleTrack> = emptyList(),
    val subtitlesEnabled: Boolean = true,
    val subtitleScale: SubtitleScalePreset = SubtitleScalePreset.NORMAL,
    val availableExternalPlayers: List<ExternalPlayerTarget> = emptyList(),
    val playbackError: PlaybackDiagnosticError? = null,
    val nextEpisode: NextEpisodeInfo? = null,
    /** Seconds left before the next episode starts automatically; null when no countdown runs. */
    val nextEpisodeCountdown: Int? = null,
    val isLoadingNextEpisode: Boolean = false,
    /** Wall-clock time the sleep timer pauses playback, or null when off. */
    val sleepTimerEndsAtMs: Long? = null,
    val sleepAtEndOfEpisode: Boolean = false,
    /** Intro / recap / credits timestamps for this episode, when someone has submitted them. */
    val skipSegments: SkipSegments = SkipSegments(),
    /** Where the "next episode" prompt appears: the real credits start, or an estimate near the end. */
    val creditsStartMs: Long? = null,
    /** True when [creditsStartMs] comes from real timestamps (then the next episode counts down). */
    val creditsFromData: Boolean = false,
    val creditsDismissed: Boolean = false,
    val isSeries: Boolean = false,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodesPanel: EpisodesPanelState = EpisodesPanelState(),
    val watchedEpisodes: Set<Pair<Int, Int>> = emptySet(),
    /** Temporary 2x speed while the screen is held down. */
    val isSpeedBoosted: Boolean = false
)

data class EpisodesPanelState(
    val seasons: List<Int> = emptyList(),
    val selectedSeason: Int? = null,
    val episodes: List<EpisodeItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

/** A preview frame for the seek bar, taken at [positionMs] (rounded to the preview step). */
data class SeekPreviewFrame(val positionMs: Long, val bitmap: Bitmap)

data class NextEpisodeInfo(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String
)

sealed interface PlayerEvent {
    /** No remembered stream matched the next episode, so let the user pick one. */
    data class OpenStreamPicker(val mediaType: MediaType, val mediaId: String, val videoId: String, val title: String) : PlayerEvent
}

@OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val okHttpClient: OkHttpClient,
    private val watchHistoryDao: WatchHistoryDao,
    private val subtitleClient: StremioSubtitleClient,
    private val externalPlayerLauncher: ExternalPlayerLauncher,
    private val preferencesRepository: UserPreferencesRepository,
    private val playbackSessionStore: PlaybackSessionStore,
    private val profileRepository: ProfileRepository,
    private val catalogRepository: CatalogRepository,
    private val addonRepository: AddonRepository,
    private val torBoxRepository: TorBoxRepository,
    private val streamChoiceDao: StreamChoiceDao,
    private val skipSegmentsClient: SkipSegmentsClient
) : ViewModel() {

    private var request: PlaybackRequest = run {
        val argUrl = savedStateHandle.get<String>("streamUrl").orEmpty()
        playbackSessionStore.peek(argUrl) ?: PlaybackRequest(
            streamUrl = argUrl,
            displayTitle = savedStateHandle.get<String>("streamTitle").orEmpty(),
            stremioVideoId = savedStateHandle.get<String>("videoId").orEmpty()
        )
    }

    private val seasonNumber: Int?
        get() = request.seasonNumber ?: request.stremioVideoId.split(":").getOrNull(1)?.toIntOrNull()
    private val episodeNumber: Int?
        get() = request.episodeNumber ?: request.stremioVideoId.split(":").getOrNull(2)?.toIntOrNull()
    private val isEpisode: Boolean
        get() = seasonNumber != null && episodeNumber != null

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            streamUrl = request.streamUrl,
            title = request.media?.title ?: request.displayTitle,
            subtitle = if (isEpisode) "Season $seasonNumber · Episode $episodeNumber" else null,
            stremioVideoId = request.stremioVideoId,
            isSeries = isEpisode && request.media?.type == MediaType.SERIES,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            availableExternalPlayers = externalPlayerLauncher.getInstalledExternalPlayers()
        )
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _events = Channel<PlayerEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private lateinit var httpFactory: OkHttpDataSource.Factory
    private var autoPlayNextEnabled = true
    private var countdownJob: Job? = null
    private var sleepJob: Job? = null
    private var preferredSubtitleLanguage: String? = null
    private var creditsCountdownStarted = false
    private var speedBeforeBoost = 1.0f

    private var previewGenerator: SeekPreviewGenerator? = null
    private val previewTarget = MutableStateFlow<Long?>(null)
    private val _seekPreview = MutableStateFlow<SeekPreviewFrame?>(null)
    /** Frame shown above the seek bar while skipping; null until one is ready. */
    val seekPreview: StateFlow<SeekPreviewFrame?> = _seekPreview.asStateFlow()

    val exoPlayer: ExoPlayer = buildPlayer()
    private val userAttachedSubtitles = mutableListOf<ExternalSubtitleTrack>()
    private var finishedHandled = false

    /** Survives ViewModel clearing so the last position is always written. */
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (!isPlaying) persistProgressNow()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _uiState.update {
                it.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    isEnded = playbackState == Player.STATE_ENDED,
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                )
            }
            if (playbackState == Player.STATE_ENDED) {
                markFinished()
                if (_uiState.value.sleepAtEndOfEpisode) {
                    // Sleep timer "end of episode": stop here instead of starting the next one.
                    _uiState.update { it.copy(sleepAtEndOfEpisode = false) }
                } else if (autoPlayNextEnabled && _uiState.value.nextEpisode != null) {
                    startNextEpisodeCountdown()
                }
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                val ratio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                _uiState.update { it.copy(videoAspectRatio = ratio) }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            refreshTrackSelections(tracks)
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.update {
                it.copy(
                    isBuffering = false,
                    isPlaying = false,
                    playbackError = PlaybackErrorClassifier.classify(error)
                )
            }
        }
    }

    init {
        exoPlayer.addListener(playerListener)
        initializePlayback()
        startProgressLoop()
        viewModelScope.launch {
            previewTarget.collectLatest { target ->
                if (target == null) return@collectLatest
                delay(60) // let fast scrubbing settle before asking for a frame
                val generator = previewGenerator
                    ?: SeekPreviewGenerator(appContext, request.streamUrl, request.requestHeaders).also { previewGenerator = it }
                generator.frameAt(target)?.let { _seekPreview.value = SeekPreviewFrame(target, it) }
            }
        }
        request.media?.takeIf { it.type == MediaType.SERIES }?.let { media ->
            viewModelScope.launch {
                catalogRepository.observeWatched(media.id).collect { marks ->
                    _uiState.update { it.copy(watchedEpisodes = marks) }
                }
            }
        }
    }

    /** Asks for a preview frame near [positionMs] (null = stopped skipping). */
    fun requestSeekPreview(positionMs: Long?) {
        previewTarget.value = positionMs?.coerceAtLeast(0L)
    }

    private fun buildPlayer(): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(appContext)
            .setEnableDecoderFallback(true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                20_000, // Min buffer
                60_000, // Max buffer for high-bitrate remux bursts
                2_500,  // Buffer before playback starts
                5_000   // Buffer after a rebuffer
            )
            .build()

        // Stream add-ons can require extra headers (behaviorHints.proxyHeaders); local subtitle files need content:// support.
        httpFactory = OkHttpDataSource.Factory(okHttpClient)
            .setDefaultRequestProperties(request.requestHeaders)
        val dataSourceFactory = DefaultDataSource.Factory(appContext, httpFactory)

        return ExoPlayer.Builder(appContext, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    private fun initializePlayback() {
        if (request.streamUrl.isBlank()) {
            _uiState.update {
                it.copy(
                    isBuffering = false,
                    playbackError = PlaybackDiagnosticError(
                        category = "MISSING_STREAM",
                        userSummary = "This stream link is no longer available. Go back and pick the stream again.",
                        technicalDetail = "EMPTY_STREAM_URL"
                    )
                )
            }
            return
        }
        viewModelScope.launch {
            val prefs = preferencesRepository.preferencesFlow.first()
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                .setPreferredAudioLanguage(prefs.preferredAudioLanguage.takeIf { it.isNotBlank() })
                .setPreferredTextLanguages(*SubtitleLanguages.priorityOrder(prefs.preferredSubtitleLanguage).toTypedArray())
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, prefs.subtitlesDisabledByDefault)
                .build()
            preferredSubtitleLanguage = prefs.preferredSubtitleLanguage
            _uiState.update { it.copy(subtitlesEnabled = !prefs.subtitlesDisabledByDefault) }
            autoPlayNextEnabled = prefs.autoPlayNextEpisode
            startCurrentRequest()
        }
    }

    /** Loads [request]: resume position, playback, external subtitles and the next-episode lookup. */
    private suspend fun startCurrentRequest() {
        val resumePositionMs = playbackKey()?.let { key ->
            watchHistoryDao.getByKey(profileRepository.currentProfileId(), key)
                ?.takeIf { it.seasonNumber == seasonNumber && it.episodeNumber == episodeNumber }
                ?.takeIf { it.durationMs <= 0L || it.positionMs < it.durationMs * FINISHED_FRACTION }
                ?.positionMs
        } ?: 0L

        // Start the video right away; external subtitles are attached once discovered.
        prepareAndPlayMedia(resumePositionMs, subtitles = emptyList())
        exoPlayer.playWhenReady = true

        viewModelScope.launch { _uiState.update { it.copy(nextEpisode = findNextEpisode()) } }
        viewModelScope.launch {
            val imdbId = request.media?.imdbId ?: request.stremioVideoId.substringBefore(":")
            val segments = skipSegmentsClient.segmentsFor(imdbId, seasonNumber, episodeNumber)
            _uiState.update { it.copy(skipSegments = segments, creditsStartMs = null) }
            updateCreditsStart()
        }

        val discoveredSubs = subtitleClient.querySubtitlesForVideo(request.stremioVideoId)
        if (discoveredSubs.isNotEmpty()) {
            _uiState.update { it.copy(externalSubtitles = discoveredSubs) }
            reloadWithSubtitles()
        }
    }

    // region Next episode

    private suspend fun findNextEpisode(): NextEpisodeInfo? {
        val media = request.media ?: return null
        val season = seasonNumber ?: return null
        val episode = episodeNumber ?: return null
        if (media.type != MediaType.SERIES) return null

        fun EpisodeItem.isAired() = airDate?.let { date ->
            runCatching { !LocalDate.parse(date.take(10)).isAfter(LocalDate.now()) }.getOrDefault(true)
        } ?: true

        val sameSeason = (catalogRepository.getSeasonEpisodes(media.id, season) as? NetworkResult.Success)?.data.orEmpty()
        sameSeason.filter { it.episodeNumber > episode }.minByOrNull { it.episodeNumber }?.let { next ->
            return if (next.isAired()) NextEpisodeInfo(next.seasonNumber, next.episodeNumber, next.title) else null
        }

        val details = (catalogRepository.getMediaDetails(MediaType.SERIES, media.id) as? NetworkResult.Success)?.data ?: return null
        val nextSeason = details.seasons.map { it.seasonNumber }.filter { it > season }.minOrNull() ?: return null
        val first = (catalogRepository.getSeasonEpisodes(media.id, nextSeason) as? NetworkResult.Success)?.data
            ?.minByOrNull { it.episodeNumber } ?: return null
        return if (first.isAired()) NextEpisodeInfo(first.seasonNumber, first.episodeNumber, first.title) else null
    }

    private fun startNextEpisodeCountdown(fromSeconds: Int = NEXT_EPISODE_COUNTDOWN_SECONDS) {
        if (countdownJob?.isActive == true) return
        countdownJob = viewModelScope.launch {
            for (seconds in fromSeconds downTo 1) {
                _uiState.update { it.copy(nextEpisodeCountdown = seconds) }
                delay(1_000)
            }
            _uiState.update { it.copy(nextEpisodeCountdown = null) }
            playNextEpisode()
        }
    }

    /** Pauses playback after [minutes]; null turns the timer off. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) {
            _uiState.update { it.copy(sleepTimerEndsAtMs = null, sleepAtEndOfEpisode = false) }
            return
        }
        val durationMs = minutes * 60_000L
        _uiState.update { it.copy(sleepTimerEndsAtMs = System.currentTimeMillis() + durationMs, sleepAtEndOfEpisode = false) }
        sleepJob = viewModelScope.launch {
            delay(durationMs)
            exoPlayer.pause()
            persistProgressNow()
            _uiState.update { it.copy(sleepTimerEndsAtMs = null) }
        }
    }

    fun setSleepAtEndOfEpisode() {
        sleepJob?.cancel()
        _uiState.update { it.copy(sleepTimerEndsAtMs = null, sleepAtEndOfEpisode = true) }
    }

    fun cancelNextEpisodeCountdown() {
        countdownJob?.cancel()
        _uiState.update { it.copy(nextEpisodeCountdown = null) }
    }

    /**
     * Plays the next episode in place. Re-uses the stream source picked last time for this show
     * (same binge group, else same add-on and quality); otherwise opens the stream list.
     */
    fun playNextEpisode() {
        val next = _uiState.value.nextEpisode ?: return
        playEpisode(next.seasonNumber, next.episodeNumber)
    }

    /** Switches to another episode of this show (from the episode list or the next-episode prompt). */
    fun playEpisode(season: Int, episode: Int) {
        countdownJob?.cancel()
        val media = request.media ?: return
        val next = NextEpisodeInfo(season, episode, "")
        if (_uiState.value.isLoadingNextEpisode) return
        val imdbId = media.imdbId ?: request.stremioVideoId.substringBefore(":").takeIf { it.startsWith("tt") } ?: return
        val nextVideoId = "$imdbId:${next.seasonNumber}:${next.episodeNumber}"
        val nextTitle = "${media.title} S${next.seasonNumber}E${next.episodeNumber}"

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingNextEpisode = true, nextEpisodeCountdown = null) }
            persistProgressNow()
            val choice = streamChoiceDao.get(profileRepository.currentProfileId(), media.id)
            val candidates = if (choice != null) addonRepository.queryAllAddonStreams(nextVideoId).first else emptyList()
            val picked = choice?.let { c ->
                candidates.firstOrNull { c.bingeGroup != null && it.bingeGroup == c.bingeGroup }
                    ?: candidates.firstOrNull { it.addonId == c.addonId && it.resolution == c.resolution }
                    ?: candidates.firstOrNull { it.addonId == c.addonId }
            }
            val outcome = picked?.let { torBoxRepository.resolveStreamCandidate(it, nextVideoId) }
            _uiState.update { it.copy(isLoadingNextEpisode = false) }

            if (picked == null || outcome !is TorBoxResolutionOutcome.ReadyToPlay) {
                _events.send(PlayerEvent.OpenStreamPicker(MediaType.SERIES, media.id, nextVideoId, nextTitle))
                return@launch
            }

            val nextRequest = PlaybackRequest(
                streamUrl = outcome.resolvedStreamUrl,
                displayTitle = nextTitle,
                stremioVideoId = nextVideoId,
                requestHeaders = picked.requestHeaders,
                media = media,
                seasonNumber = next.seasonNumber,
                episodeNumber = next.episodeNumber
            )
            playbackSessionStore.put(nextRequest)
            request = nextRequest
            httpFactory.setDefaultRequestProperties(nextRequest.requestHeaders)
            finishedHandled = false
            creditsCountdownStarted = false
            userAttachedSubtitles.clear()
            previewGenerator?.release()
            previewGenerator = null
            _seekPreview.value = null
            _uiState.update {
                it.copy(
                    streamUrl = nextRequest.streamUrl,
                    subtitle = "Season ${next.seasonNumber} · Episode ${next.episodeNumber}",
                    stremioVideoId = nextVideoId,
                    seasonNumber = next.seasonNumber,
                    episodeNumber = next.episodeNumber,
                    skipSegments = SkipSegments(),
                    creditsStartMs = null,
                    creditsFromData = false,
                    creditsDismissed = false,
                    nextEpisodeCountdown = null,
                    currentPositionMs = 0L,
                    durationMs = 0L,
                    nextEpisode = null,
                    externalSubtitles = emptyList(),
                    playbackError = null,
                    isEnded = false,
                    isBuffering = true
                )
            }
            startCurrentRequest()
        }
    }

    /** "Watch credits": hide the prompt and stop the countdown; the next episode still follows at the very end. */
    fun dismissCredits() {
        countdownJob?.cancel()
        _uiState.update { it.copy(creditsDismissed = true, nextEpisodeCountdown = null) }
    }

    /** Uses the real credits timestamp when it fits this file, otherwise a short window before the end. */
    private fun updateCreditsStart() {
        val duration = exoPlayer.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: return
        val fromData = _uiState.value.skipSegments.credits?.startMs
            ?.takeIf { it in (duration / 2)..(duration - 5_000L) }
        val estimate = duration - (duration * 3 / 100).coerceIn(40_000L, 120_000L)
        _uiState.update { it.copy(creditsStartMs = fromData ?: estimate, creditsFromData = fromData != null) }
    }

    // region Episode list

    fun openEpisodesPanel() {
        val media = request.media ?: return
        val panel = _uiState.value.episodesPanel
        if (panel.seasons.isNotEmpty() || panel.isLoading) return
        _uiState.update { it.copy(episodesPanel = EpisodesPanelState(isLoading = true)) }
        viewModelScope.launch {
            val seasons = (catalogRepository.getMediaDetails(MediaType.SERIES, media.id) as? NetworkResult.Success)
                ?.data?.seasons?.map { it.seasonNumber }?.filter { it > 0 }.orEmpty()
                .ifEmpty { listOfNotNull(seasonNumber) }
            _uiState.update { it.copy(episodesPanel = it.episodesPanel.copy(seasons = seasons)) }
            val first = seasonNumber?.takeIf { it in seasons } ?: seasons.firstOrNull()
            if (first == null) {
                _uiState.update { it.copy(episodesPanel = it.episodesPanel.copy(isLoading = false, error = "Episode list unavailable.")) }
            } else {
                selectEpisodesSeason(first)
            }
        }
    }

    fun selectEpisodesSeason(season: Int) {
        val media = request.media ?: return
        _uiState.update { it.copy(episodesPanel = it.episodesPanel.copy(selectedSeason = season, isLoading = true, error = null)) }
        viewModelScope.launch {
            when (val res = catalogRepository.getSeasonEpisodes(media.id, season)) {
                is NetworkResult.Success -> _uiState.update {
                    if (it.episodesPanel.selectedSeason != season) it
                    else it.copy(episodesPanel = it.episodesPanel.copy(episodes = res.data, isLoading = false))
                }
                is NetworkResult.Error -> _uiState.update {
                    it.copy(episodesPanel = it.episodesPanel.copy(episodes = emptyList(), isLoading = false, error = res.userMessage))
                }
            }
        }
    }

    // endregion

    private fun prepareAndPlayMedia(resumePositionMs: Long, subtitles: List<ExternalSubtitleTrack>) {
        // Albanian, then English (after the user's own choice) come first; the best match is the default track.
        val ordered = subtitles.sortedBy { SubtitleLanguages.rank(it.languageCode, preferredSubtitleLanguage) }
        val defaultId = ordered.firstOrNull()?.takeIf {
            SubtitleLanguages.rank(it.languageCode, preferredSubtitleLanguage) < SubtitleLanguages.priorityOrder(preferredSubtitleLanguage).size
        }?.id
        val subtitleConfigs = ordered.map { sub ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                .setSelectionFlags(if (sub.id == defaultId) C.SELECTION_FLAG_DEFAULT else 0)
                .setMimeType(if (sub.mimeType == "text/vtt") MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
                .setLanguage(SubtitleLanguages.normalize(sub.languageCode))
                .setLabel(sub.label)
                .setId(sub.id)
                .build()
        }

        val mediaItem = MediaItem.Builder()
            .setUri(request.streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(_uiState.value.title)
                    .build()
            )
            .setSubtitleConfigurations(subtitleConfigs)
            .build()

        exoPlayer.setMediaItem(mediaItem, resumePositionMs.coerceAtLeast(0L))
        exoPlayer.prepare()
    }

    private fun reloadWithSubtitles() {
        val playWhenReady = exoPlayer.playWhenReady
        prepareAndPlayMedia(
            resumePositionMs = exoPlayer.currentPosition,
            subtitles = _uiState.value.externalSubtitles + userAttachedSubtitles
        )
        exoPlayer.playWhenReady = playWhenReady
    }

    fun togglePlayPause() {
        when {
            exoPlayer.playbackState == Player.STATE_ENDED -> {
                exoPlayer.seekTo(0L)
                exoPlayer.play()
            }
            exoPlayer.isPlaying -> exoPlayer.pause()
            else -> exoPlayer.play()
        }
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun seekRelative(deltaMs: Long) {
        val duration = exoPlayer.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, duration)
        exoPlayer.seekTo(target)
        _uiState.update { it.copy(currentPositionMs = target) }
    }

    fun seekTo(positionMs: Long) {
        val duration = exoPlayer.duration
        if (duration <= 0L) return
        val target = positionMs.coerceIn(0L, duration)
        exoPlayer.seekTo(target)
        _uiState.update { it.copy(currentPositionMs = target) }
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer.playbackParameters = PlaybackParameters(speed)
        _uiState.update { it.copy(playbackSpeed = speed, isSpeedBoosted = false) }
    }

    /** Hold-to-speed-up: 2x while [boosted], then back to the chosen speed. */
    fun setSpeedBoost(boosted: Boolean) {
        if (boosted == _uiState.value.isSpeedBoosted) return
        if (boosted) {
            speedBeforeBoost = _uiState.value.playbackSpeed
            exoPlayer.playbackParameters = PlaybackParameters(2.0f)
            if (!exoPlayer.isPlaying) exoPlayer.play()
        } else {
            exoPlayer.playbackParameters = PlaybackParameters(speedBeforeBoost)
        }
        _uiState.update { it.copy(isSpeedBoosted = boosted) }
    }

    fun cycleSubtitleScale() {
        val presets = SubtitleScalePreset.entries
        val nextIndex = (presets.indexOf(_uiState.value.subtitleScale) + 1) % presets.size
        _uiState.update { it.copy(subtitleScale = presets[nextIndex]) }
    }

    fun selectAudioTrack(track: SelectableTrackInfo) {
        val groups = exoPlayer.currentTracks.groups
        if (track.groupIndex !in groups.indices) return
        val mediaTrackGroup = groups[track.groupIndex].mediaTrackGroup
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(mediaTrackGroup, track.trackIndex))
            .build()
    }

    fun selectSubtitleTrack(track: SelectableTrackInfo?) {
        if (track == null) {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            _uiState.update { it.copy(subtitlesEnabled = false) }
            return
        }

        val groups = exoPlayer.currentTracks.groups
        if (track.groupIndex !in groups.indices) return
        val mediaTrackGroup = groups[track.groupIndex].mediaTrackGroup
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(mediaTrackGroup, track.trackIndex))
            .build()
        _uiState.update { it.copy(subtitlesEnabled = true) }
    }

    fun attachCustomSubtitleUri(uri: Uri, label: String) {
        userAttachedSubtitles.add(
            ExternalSubtitleTrack(
                id = "local_${System.currentTimeMillis()}",
                url = uri.toString(),
                languageCode = "und",
                label = "Local file: $label",
                addonName = "Device storage",
                mimeType = StremioSubtitleClient.detectSubtitleMimeType(label)
            )
        )
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        _uiState.update { it.copy(subtitlesEnabled = true) }
        reloadWithSubtitles()
    }

    fun retryPlayback() {
        _uiState.update { it.copy(playbackError = null, isBuffering = true) }
        prepareAndPlayMedia(
            resumePositionMs = exoPlayer.currentPosition,
            subtitles = _uiState.value.externalSubtitles + userAttachedSubtitles
        )
        exoPlayer.playWhenReady = true
    }

    fun handoffToExternalPlayer(target: ExternalPlayerTarget): Boolean {
        exoPlayer.pause()
        persistProgressNow()
        val launched = externalPlayerLauncher.launchExternalPlayer(
            target = target,
            streamUrl = request.streamUrl,
            title = _uiState.value.title,
            resumePositionMs = exoPlayer.currentPosition,
            headers = request.requestHeaders
        )
        if (!launched) exoPlayer.play()
        return launched
    }

    private fun refreshTrackSelections(tracks: Tracks) {
        val externalLabels = (_uiState.value.externalSubtitles + userAttachedSubtitles).map { it.label }.toSet()
        val audioList = mutableListOf<SelectableTrackInfo>()
        val subList = mutableListOf<SelectableTrackInfo>()

        tracks.groups.forEachIndexed { groupIdx, group ->
            for (trackIdx in 0 until group.length) {
                if (!group.isTrackSupported(trackIdx)) continue
                val format = group.getTrackFormat(trackIdx)
                val lang = format.language ?: "und"
                val label = format.label ?: languageDisplayName(lang)
                val isSelected = group.isTrackSelected(trackIdx)

                when (group.type) {
                    C.TRACK_TYPE_AUDIO -> {
                        val channels = when (format.channelCount) {
                            1 -> " · Mono"
                            2 -> " · Stereo"
                            6 -> " · 5.1"
                            8 -> " · 7.1"
                            else -> if (format.channelCount > 0) " · ${format.channelCount}ch" else ""
                        }
                        val codec = format.sampleMimeType?.substringAfter('/')?.uppercase(Locale.US)?.let { " · $it" }.orEmpty()
                        audioList.add(
                            SelectableTrackInfo(
                                groupIndex = groupIdx,
                                trackIndex = trackIdx,
                                id = "${groupIdx}_$trackIdx",
                                label = "$label$codec$channels",
                                language = lang,
                                isSelected = isSelected
                            )
                        )
                    }
                    C.TRACK_TYPE_TEXT -> {
                        val isExternal = format.label in externalLabels
                        subList.add(
                            SelectableTrackInfo(
                                groupIndex = groupIdx,
                                trackIndex = trackIdx,
                                id = "${groupIdx}_$trackIdx",
                                label = if (isExternal) label else "$label (built-in)",
                                language = lang,
                                isSelected = isSelected,
                                isExternal = isExternal
                            )
                        )
                    }
                }
            }
        }

        val sortedSubs = subList.sortedBy { SubtitleLanguages.rank(it.language, preferredSubtitleLanguage) }
        _uiState.update { it.copy(audioTracks = audioList, subtitleTracks = sortedSubs) }
    }

    private fun startProgressLoop() {
        viewModelScope.launch {
            var tick = 0
            while (isActive) {
                delay(500)
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                val dur = exoPlayer.duration.coerceAtLeast(0L)
                _uiState.update {
                    it.copy(
                        currentPositionMs = pos,
                        durationMs = dur,
                        bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                    )
                }
                tick++
                if (dur > 0L && _uiState.value.creditsStartMs == null) updateCreditsStart()
                checkCreditsReached(pos)
                if (tick % 20 == 0 && exoPlayer.isPlaying) persistProgressNow()
                if (dur > 0L && pos >= dur * FINISHED_FRACTION) markFinished()
            }
        }
    }

    /** At the credits (real timestamps only), count down to the next episode like streaming apps do. */
    private fun checkCreditsReached(positionMs: Long) {
        val state = _uiState.value
        val creditsStart = state.creditsStartMs ?: return
        if (creditsCountdownStarted || !state.creditsFromData || state.creditsDismissed) return
        if (positionMs < creditsStart || !exoPlayer.isPlaying) return
        if (state.nextEpisode == null || !autoPlayNextEnabled || state.sleepAtEndOfEpisode) return
        creditsCountdownStarted = true
        startNextEpisodeCountdown(CREDITS_COUNTDOWN_SECONDS)
    }

    private fun playbackKey(): String? {
        val baseId = request.media?.id ?: request.stremioVideoId.substringBefore(":").takeIf { it.isNotBlank() }
        return baseId?.let { "${mediaType().name.lowercase(Locale.US)}:$it" }
    }

    private fun mediaType(): MediaType = request.media?.type ?: if (isEpisode) MediaType.SERIES else MediaType.MOVIE

    /** Writes the current position to watch history (called while playing, on pause and on exit). */
    fun persistProgressNow() {
        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
        val dur = exoPlayer.duration.coerceAtLeast(0L)
        if (finishedHandled || pos < MIN_POSITION_TO_SAVE_MS || dur <= 0L) return
        if (pos >= dur * FINISHED_FRACTION) {
            markFinished()
            return
        }
        val key = playbackKey() ?: return
        val media = request.media
        val baseId = key.substringAfter(':')
        val entity = WatchHistoryEntity(
            profileId = profileRepository.currentProfileId(),
            playbackKey = key,
            mediaId = baseId,
            tmdbId = media?.tmdbId,
            imdbId = media?.imdbId ?: baseId.takeIf { it.startsWith("tt") },
            title = media?.title ?: request.displayTitle,
            mediaType = mediaType(),
            posterUrl = media?.posterUrl,
            backdropUrl = media?.backdropUrl,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeTitle = if (isEpisode) "S${seasonNumber}E$episodeNumber" else null,
            positionMs = pos,
            durationMs = dur,
            lastStreamUrlHash = request.streamUrl.hashCode().toString(),
            updatedAtMs = System.currentTimeMillis()
        )
        persistScope.launch { watchHistoryDao.upsertWatchProgress(entity) }
    }

    /** Removes a finished title from Continue Watching. */
    private fun markFinished() {
        if (finishedHandled) return
        finishedHandled = true
        val key = playbackKey() ?: return
        val profileId = profileRepository.currentProfileId()
        val baseId = key.substringAfter(':')
        val season = seasonNumber ?: 0
        val episode = episodeNumber ?: 0
        persistScope.launch {
            watchHistoryDao.removeWatchProgress(profileId, key)
            catalogRepository.setWatched(baseId, season, episode, watched = true)
        }
    }

    override fun onCleared() {
        persistProgressNow()
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
        previewGenerator?.release()
        super.onCleared()
    }

    companion object {
        const val SEEK_STEP_MS = 10_000L
        private const val NEXT_EPISODE_COUNTDOWN_SECONDS = 8
        private const val CREDITS_COUNTDOWN_SECONDS = 10
        private const val MIN_POSITION_TO_SAVE_MS = 15_000L
        private const val FINISHED_FRACTION = 0.94

        fun languageDisplayName(code: String): String {
            if (code == "und" || code.isBlank()) return "Unknown"
            val name = Locale.forLanguageTag(code).displayLanguage
            return name.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) } ?: code.uppercase(Locale.US)
        }
    }
}
