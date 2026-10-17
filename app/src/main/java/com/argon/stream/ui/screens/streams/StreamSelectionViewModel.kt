package com.argon.stream.ui.screens.streams

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.argon.stream.core.download.DownloadStartResult
import com.argon.stream.core.download.StreamDownloader
import com.argon.stream.core.network.NetworkResult
import com.argon.stream.core.player.ExternalPlayerLauncher
import com.argon.stream.core.player.PlaybackRequest
import com.argon.stream.core.player.PlaybackSessionStore
import com.argon.stream.data.local.UserPreferencesRepository
import com.argon.stream.data.remote.addon.StreamMetadataParser
import com.argon.stream.data.repository.AddonRepository
import com.argon.stream.data.repository.CatalogRepository
import com.argon.stream.data.repository.TorBoxRepository
import com.argon.stream.domain.model.ExternalPlayerTarget
import com.argon.stream.domain.model.MediaItem
import com.argon.stream.domain.model.MediaType
import com.argon.stream.domain.model.StreamCandidate
import com.argon.stream.domain.model.TorBoxMediaFile
import com.argon.stream.domain.model.TorBoxResolutionOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class ReadyStreamLaunchEvent(
    val streamUrl: String,
    val title: String
)

data class MultiFilePromptState(
    val torrentId: Int,
    val torrentName: String,
    val files: List<TorBoxMediaFile>,
    val forDownload: Boolean = false
)

data class StreamSelectionUiState(
    val stremioVideoId: String = "",
    val title: String = "",
    val isLoading: Boolean = true,
    val isResolving: Boolean = false,
    val statusBanner: String? = null,
    val isStatusError: Boolean = false,
    val selectedResolutionFilter: String? = null,
    val availableResolutions: List<String> = emptyList(),
    val streams: List<StreamCandidate> = emptyList(),
    val hiddenByQualityLimit: Int = 0,
    val maxResolution: String = "2160p",
    val hasStreamAddons: Boolean = true,
    val providerWarnings: List<String> = emptyList(),
    val multiFilePrompt: MultiFilePromptState? = null
)

@HiltViewModel
class StreamSelectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val addonRepository: AddonRepository,
    private val torBoxRepository: TorBoxRepository,
    private val catalogRepository: CatalogRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val playbackSessionStore: PlaybackSessionStore,
    private val externalPlayerLauncher: ExternalPlayerLauncher,
    private val streamDownloader: StreamDownloader
) : ViewModel() {

    val stremioVideoId: String = savedStateHandle["videoId"] ?: ""
    private val displayTitle: String = savedStateHandle["title"] ?: ""
    private val mediaId: String = savedStateHandle["mediaId"] ?: ""
    private val mediaType: MediaType = runCatching {
        MediaType.valueOf(savedStateHandle["type"] ?: "")
    }.getOrElse { if (stremioVideoId.contains(":")) MediaType.SERIES else MediaType.MOVIE }

    private val idParts = stremioVideoId.split(":")
    private val seasonNumber: Int? = idParts.getOrNull(1)?.toIntOrNull()
    private val episodeNumber: Int? = idParts.getOrNull(2)?.toIntOrNull()

    private val _uiState = MutableStateFlow(
        StreamSelectionUiState(stremioVideoId = stremioVideoId, title = displayTitle)
    )
    val uiState: StateFlow<StreamSelectionUiState> = _uiState.asStateFlow()

    private val _launchPlayerEvent = Channel<ReadyStreamLaunchEvent>(Channel.BUFFERED)
    val launchPlayerEvent = _launchPlayerEvent.receiveAsFlow()

    private var allStreams: List<StreamCandidate> = emptyList()
    private var mediaItem: MediaItem? = null

    init {
        viewModelScope.launch {
            if (mediaId.isNotBlank()) {
                (catalogRepository.getMediaDetails(mediaType, mediaId) as? NetworkResult.Success)
                    ?.let { mediaItem = it.data.item }
            }
        }
        refreshStreams()
    }

    fun refreshStreams() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusBanner = null) }
            addonRepository.ensureDefaultAddonsSeeded()
            val prefs = preferencesRepository.preferencesFlow.first()
            val hasStreamAddons = addonRepository.observeAddons().first().any { addon ->
                addon.isEnabled && addon.resources.any { it.equals("stream", ignoreCase = true) }
            }
            val (candidates, warnings) = addonRepository.queryAllAddonStreams(stremioVideoId)

            // Enrich with live TorBox /torrents/checkcached status when the user has TorBox configured
            val enriched = if (torBoxRepository.hasApiKey()) {
                val cachedSet = torBoxRepository.checkCachedHashes(candidates.mapNotNull { it.infoHash })
                candidates.map { c ->
                    c.copy(isCachedOnDebrid = c.isCachedOnDebrid || c.infoHash?.lowercase(Locale.US) in cachedSet)
                }
            } else {
                candidates
            }

            val sorted = enriched.sortedWith(
                compareByDescending<StreamCandidate> { prefs.preferCachedDebridStreams && it.isCachedOnDebrid }
                    .thenByDescending { StreamMetadataParser.resolutionScore(it.resolution) }
                    .thenByDescending { it.seeders ?: 0 }
            )

            val maxScore = StreamMetadataParser.resolutionScore(prefs.preferredMaxResolution)
                .takeIf { it > 0 } ?: Int.MAX_VALUE
            val withinLimit = sorted.filter { StreamMetadataParser.resolutionScore(it.resolution) <= maxScore }

            allStreams = withinLimit
            _uiState.update { state ->
                val resolutions = withinLimit.map { it.resolution }.distinct()
                    .sortedByDescending { StreamMetadataParser.resolutionScore(it) }
                val filter = state.selectedResolutionFilter?.takeIf { it in resolutions }
                state.copy(
                    isLoading = false,
                    availableResolutions = resolutions,
                    selectedResolutionFilter = filter,
                    streams = applyFilter(filter),
                    hiddenByQualityLimit = sorted.size - withinLimit.size,
                    maxResolution = prefs.preferredMaxResolution,
                    hasStreamAddons = hasStreamAddons,
                    providerWarnings = warnings
                )
            }
        }
    }

    private fun applyFilter(resolution: String?): List<StreamCandidate> =
        if (resolution == null) allStreams else allStreams.filter { it.resolution == resolution }

    fun setResolutionFilter(resolution: String?) {
        _uiState.update { it.copy(selectedResolutionFilter = resolution, streams = applyFilter(resolution)) }
    }

    fun dismissStatusDialog() {
        _uiState.update { it.copy(statusBanner = null, isStatusError = false) }
    }

    fun onCandidateClicked(candidate: StreamCandidate) = resolveCandidate(candidate, forDownload = false)

    fun onDownloadClicked(candidate: StreamCandidate) = resolveCandidate(candidate, forDownload = true)

    private fun resolveCandidate(candidate: StreamCandidate, forDownload: Boolean) {
        if (_uiState.value.isResolving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isResolving = true, statusBanner = null) }
            val outcome = torBoxRepository.resolveStreamCandidate(candidate, stremioVideoId)
            _uiState.update { it.copy(isResolving = false) }
            when (outcome) {
                is TorBoxResolutionOutcome.ReadyToPlay -> if (forDownload) {
                    val name = candidate.filenameHint
                        ?: if (outcome.torrentId == -1) displayTitle else outcome.fileName
                    startDownload(outcome.resolvedStreamUrl, name, candidate.requestHeaders)
                } else {
                    launchPlayback(streamUrl = outcome.resolvedStreamUrl, headers = candidate.requestHeaders)
                }
                is TorBoxResolutionOutcome.MultiFileSelectionRequired -> _uiState.update {
                    it.copy(
                        multiFilePrompt = MultiFilePromptState(
                            torrentId = outcome.torrentId,
                            torrentName = outcome.torrentName,
                            files = outcome.playableFiles,
                            forDownload = forDownload
                        )
                    )
                }
                is TorBoxResolutionOutcome.QueuedProcessing -> showStatus(
                    String.format(
                        Locale.US,
                        "TorBox is downloading this torrent (%s, %.0f%%). Try again when it finishes, or pick a stream marked Instant.",
                        outcome.statusText,
                        outcome.progressPercent
                    ),
                    isError = false
                )
                is TorBoxResolutionOutcome.Failed -> showStatus(outcome.userMessage, isError = true)
            }
        }
    }

    fun onMultiFileSelected(torrentId: Int, file: TorBoxMediaFile) {
        val forDownload = _uiState.value.multiFilePrompt?.forDownload == true
        _uiState.update { it.copy(multiFilePrompt = null) }
        viewModelScope.launch {
            _uiState.update { it.copy(isResolving = true) }
            val outcome = torBoxRepository.requestDirectLinkForFile(torrentId, file.id, file.shortName)
            _uiState.update { it.copy(isResolving = false) }
            when (outcome) {
                is TorBoxResolutionOutcome.ReadyToPlay -> if (forDownload) {
                    startDownload(outcome.resolvedStreamUrl, file.shortName, emptyMap())
                } else {
                    launchPlayback(outcome.resolvedStreamUrl, emptyMap())
                }
                is TorBoxResolutionOutcome.Failed -> showStatus(outcome.userMessage, isError = true)
                else -> Unit
            }
        }
    }

    fun dismissMultiFileDialog() {
        _uiState.update { it.copy(multiFilePrompt = null) }
    }

    private fun startDownload(url: String, name: String, headers: Map<String, String>) {
        when (val result = streamDownloader.enqueue(url, name, headers)) {
            is DownloadStartResult.Started -> showStatus(
                "Downloading \"${result.fileName}\" to ${result.locationLabel}. Follow progress and play it from the Downloads tab.",
                isError = false
            )
            is DownloadStartResult.NotSupported -> showStatus(result.reason, isError = true)
        }
    }

    private suspend fun launchPlayback(streamUrl: String, headers: Map<String, String>) {
        val prefs = preferencesRepository.preferencesFlow.first()
        if (prefs.defaultPlayer != ExternalPlayerTarget.INTERNAL_EXOPLAYER) {
            val launched = externalPlayerLauncher.launchExternalPlayer(
                target = prefs.defaultPlayer,
                streamUrl = streamUrl,
                title = displayTitle,
                headers = headers
            )
            if (launched) return
            showStatus("${prefs.defaultPlayer.displayName} isn't available, so the built-in player was used.", isError = false)
        }

        playbackSessionStore.put(
            PlaybackRequest(
                streamUrl = streamUrl,
                displayTitle = displayTitle,
                stremioVideoId = stremioVideoId,
                requestHeaders = headers,
                media = mediaItem,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber
            )
        )
        _launchPlayerEvent.send(ReadyStreamLaunchEvent(streamUrl = streamUrl, title = displayTitle))
    }

    private fun showStatus(message: String, isError: Boolean) {
        _uiState.update { it.copy(statusBanner = message, isStatusError = isError) }
    }
}
