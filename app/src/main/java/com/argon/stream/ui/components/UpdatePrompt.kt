package com.argon.stream.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.argon.stream.BuildConfig
import com.argon.stream.core.update.AppUpdate
import com.argon.stream.core.update.UpdateChecker
import com.argon.stream.core.update.UpdateDownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class UpdatePromptState(
    val update: AppUpdate? = null,
    val download: UpdateDownloadState = UpdateDownloadState.Idle
)

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val checker: UpdateChecker
) : ViewModel() {
    private val _state = MutableStateFlow(UpdatePromptState())
    val state: StateFlow<UpdatePromptState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            delay(1_500) // let the app settle first
            checker.findUpdate()?.takeUnless { checker.isSkipped(it.version) }?.let { update ->
                _state.value = UpdatePromptState(update)
            }
        }
    }

    fun later() {
        _state.value = UpdatePromptState()
    }

    fun skip() {
        _state.value.update?.let { checker.skip(it.version) }
        _state.value = UpdatePromptState()
    }

    fun download() {
        val update = _state.value.update ?: return
        if (_state.value.download is UpdateDownloadState.Downloading) return
        val id = runCatching { checker.startDownload(update) }.getOrElse {
            _state.value = _state.value.copy(download = UpdateDownloadState.Failed("Android's download manager isn't available."))
            return
        }
        _state.value = _state.value.copy(download = UpdateDownloadState.Downloading(null))
        viewModelScope.launch {
            while (true) {
                val status = checker.downloadState(id)
                _state.value = _state.value.copy(download = status)
                if (status !is UpdateDownloadState.Downloading) break
                delay(500)
            }
        }
    }

    fun install(context: Context) {
        val ready = _state.value.download as? UpdateDownloadState.Ready ?: return
        if (checker.needsInstallPermission()) {
            runCatching { context.startActivity(checker.installPermissionIntent()) }
            return
        }
        val intent = checker.installIntent(ready.downloadId)
        if (intent == null || runCatching { context.startActivity(intent) }.isFailure) {
            _state.value = _state.value.copy(download = UpdateDownloadState.Failed("Couldn't open the installer."))
        }
    }
}

/** "Update available" pop-up shown at launch when GitHub has a newer release. */
@Composable
fun UpdatePrompt(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val update = state.update ?: return
    val context = LocalContext.current
    val download = state.download

    AlertDialog(
        onDismissRequest = { if (download !is UpdateDownloadState.Downloading) viewModel.later() },
        icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
        title = { Text("Update available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Argon ${update.version} is ready (you have ${BuildConfig.VERSION_NAME})." +
                        if (update.apkSizeBytes > 0) " Size: ${formatMb(update.apkSizeBytes)}." else "",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (update.notes.isNotBlank()) {
                    Column(modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                        Text(update.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                when (download) {
                    is UpdateDownloadState.Downloading -> {
                        val progress = download.progress
                        if (progress != null) {
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Text("Downloading…", style = MaterialTheme.typography.labelMedium)
                    }
                    is UpdateDownloadState.Ready -> Text(
                        "Downloaded. Tap Install; Android may first ask you to allow installs from Argon.",
                        style = MaterialTheme.typography.labelMedium
                    )
                    is UpdateDownloadState.Failed -> Text(download.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                    UpdateDownloadState.Idle -> Unit
                }
            }
        },
        confirmButton = {
            when (download) {
                is UpdateDownloadState.Ready -> Button(onClick = { viewModel.install(context) }) { Text("Install") }
                is UpdateDownloadState.Downloading -> Unit
                else -> Button(onClick = viewModel::download) { Text("Update") }
            }
        },
        dismissButton = {
            if (download !is UpdateDownloadState.Downloading) {
                TextButton(onClick = viewModel::later) { Text("Later") }
                if (download is UpdateDownloadState.Idle) {
                    TextButton(onClick = viewModel::skip) { Text("Skip version") }
                }
            }
        }
    )
}

private fun formatMb(bytes: Long): String = String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
