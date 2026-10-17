package com.argon.stream.ui.screens.downloads

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.argon.stream.core.download.DownloadItem
import com.argon.stream.core.download.DownloadsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DownloadsUiState(
    val isLoading: Boolean = true,
    val items: List<DownloadItem> = emptyList()
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: DownloadsRepository
) : ViewModel() {

    private val refreshTick = MutableStateFlow(0)

    /** Polls once a second while the screen is visible so progress bars move live. */
    val uiState: StateFlow<DownloadsUiState> = combine(
        flow {
            while (true) {
                emit(repository.list())
                delay(1_000)
            }
        },
        refreshTick
    ) { items, _ -> DownloadsUiState(isLoading = false, items = items) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), DownloadsUiState())

    fun playableUri(item: DownloadItem): Uri? = repository.playableUri(item.id)

    fun remove(item: DownloadItem) {
        repository.remove(item.id)
        refreshTick.value++
    }
}
