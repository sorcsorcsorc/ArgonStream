package com.argon.stream.ui.screens.player

import android.content.Context
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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val playbackError: PlaybackDiagnosticError? = null
)

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
    playbackSessionStore: PlaybackSessionStore
) : ViewModel() {

    private val request: PlaybackRequest = run {
        val argUrl = savedStateHandle.get<String>("streamUrl").orEmpty()
        playbackSessionStore.peek(argUrl) ?: PlaybackRequest(
            streamUrl = argUrl,
            displayTitle = savedStateHandle.get<String>("streamTitle").orEmpty(),
            stremioVideoId = savedStateHandle.get<String>("videoId").orEmpty()
        )
    }

    private val idParts = request.stremioVideoId.split(":")
    private val seasonNumber: Int? = request.seasonNumber ?: idParts.getOrNull(1)?.toIntOrNull()
    private val episodeNumber: Int? = request.episodeNumber ?: idParts.getOrNull(2)?.toIntOrNull()
    private val isEpisode = seasonNumber != null && episodeNumber != null

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            streamUrl = request.streamUrl,
            title = request.media?.title ?: request.displayTitle,
            subtitle = if (isEpisode) "Season $seasonNumber · Episode $episodeNumber" else null,
            stremioVideoId = request.stremioVideoId,
            availableExternalPlayers = externalPlayerLauncher.getInstalledExternalPlayers()
        )
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

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
            if (playbackState == Player.STATE_ENDED) markFinished()
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
        val httpFactory = OkHttpDataSource.Factory(okHttpClient)
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
                .setPreferredTextLanguage(prefs.preferredSubtitleLanguage.takeIf { it.isNotBlank() })
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, prefs.subtitlesDisabledByDefault)
                .build()
            _uiState.update { it.copy(subtitlesEnabled = !prefs.subtitlesDisabledByDefault) }

            val resumePositionMs = playbackKey()?.let { key ->
                watchHistoryDao.getByKey(key)
                    ?.takeIf { it.seasonNumber == seasonNumber && it.episodeNumber == episodeNumber }
                    ?.takeIf { it.durationMs <= 0L || it.positionMs < it.durationMs * FINISHED_FRACTION }
                    ?.positionMs
            } ?: 0L

            // Start the video right away; external subtitles are attached once discovered.
            prepareAndPlayMedia(resumePositionMs, subtitles = emptyList())
            exoPlayer.playWhenReady = true

            val discoveredSubs = subtitleClient.querySubtitlesForVideo(request.stremioVideoId)
            if (discoveredSubs.isNotEmpty()) {
                _uiState.update { it.copy(externalSubtitles = discoveredSubs) }
                reloadWithSubtitles()
            }
        }
    }

    private fun prepareAndPlayMedia(resumePositionMs: Long, subtitles: List<ExternalSubtitleTrack>) {
        val subtitleConfigs = subtitles.map { sub ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                .setMimeType(if (sub.mimeType == "text/vtt") MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
                .setLanguage(sub.languageCode)
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
        _uiState.update { it.copy(playbackSpeed = speed) }
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

        _uiState.update { it.copy(audioTracks = audioList, subtitleTracks = subList) }
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
                if (tick % 20 == 0 && exoPlayer.isPlaying) persistProgressNow()
                if (dur > 0L && pos >= dur * FINISHED_FRACTION) markFinished()
            }
        }
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
        persistScope.launch { watchHistoryDao.removeWatchProgress(key) }
    }

    override fun onCleared() {
        persistProgressNow()
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
        super.onCleared()
    }

    companion object {
        const val SEEK_STEP_MS = 10_000L
        private const val MIN_POSITION_TO_SAVE_MS = 15_000L
        private const val FINISHED_FRACTION = 0.94

        fun languageDisplayName(code: String): String {
            if (code == "und" || code.isBlank()) return "Unknown"
            val name = Locale.forLanguageTag(code).displayLanguage
            return name.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) } ?: code.uppercase(Locale.US)
        }
    }
}
