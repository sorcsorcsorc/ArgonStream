package com.argon.stream.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.argon.stream.BuildConfig
import com.argon.stream.domain.model.ExternalPlayerTarget
import com.argon.stream.ui.components.ScreenHeader
import com.argon.stream.ui.components.SectionCard
import com.argon.stream.ui.components.StatusMessageCard

private val LANGUAGE_OPTIONS = listOf(
    "en" to "English", "es" to "Spanish", "fr" to "French", "de" to "German", "it" to "Italian",
    "pt" to "Portuguese", "nl" to "Dutch", "pl" to "Polish", "ru" to "Russian", "tr" to "Turkish",
    "ar" to "Arabic", "hi" to "Hindi", "ja" to "Japanese", "ko" to "Korean", "zh" to "Chinese"
)

private enum class ConfirmAction { CLEAR_HISTORY, WIPE_ALL }

/** UI actions, implemented by [SettingsViewModel]; previews and screenshot tests can pass a no-op object. */
interface SettingsActions {
    fun dismissStatus() {}
    fun onTmdbKeyChange(value: String) {}
    fun saveTmdbKey() {}
    fun removeTmdbKey() {}
    fun onTorBoxKeyChange(value: String) {}
    fun saveAndTestTorBoxKey() {}
    fun testTorBoxConnection(showStatus: Boolean = true) {}
    fun disconnectTorBox() {}
    fun setDefaultPlayer(target: ExternalPlayerTarget) {}
    fun setSubtitlesDisabledByDefault(disabled: Boolean) {}
    fun setPreferredAudioLanguage(code: String) {}
    fun setPreferredSubtitleLanguage(code: String) {}
    fun setMaxResolution(resolution: String) {}
    fun setPreferCached(preferCached: Boolean) {}
    fun setDarkTheme(enabled: Boolean) {}
    fun setDynamicColors(enabled: Boolean) {}
    fun clearImageCache() {}
    fun clearWatchHistory() {}
    fun wipeAllDataAndDisconnect() {}
    fun refreshDiagnostics() {}
    fun exportBackupTo(uri: Uri) {}
    fun importBackupFrom(uri: Uri) {}
    fun clearCrashLog() {}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(state = state, actions = viewModel)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    actions: SettingsActions
) {
    val prefs = state.preferences
    var pendingConfirm by remember { mutableStateOf<ConfirmAction?>(null) }
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    var languagePicker by remember { mutableStateOf<String?>(null) } // "audio" or "subtitle"
    val context = LocalContext.current
    val backupSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(actions::exportBackupTo)
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(actions::importBackupFrom)
    }

    pendingConfirm?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingConfirm = null },
            title = { Text(if (action == ConfirmAction.WIPE_ALL) "Wipe everything?" else "Clear watch history?") },
            text = {
                Text(
                    if (action == ConfirmAction.WIPE_ALL) {
                        "This removes your watchlist, history, add-ons, settings and saved TorBox/TMDB keys from this device."
                    } else {
                        "Continue Watching and resume positions will be removed."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (action == ConfirmAction.WIPE_ALL) actions.wipeAllDataAndDisconnect() else actions.clearWatchHistory()
                    pendingConfirm = null
                }) { Text(if (action == ConfirmAction.WIPE_ALL) "Wipe" else "Clear") }
            },
            dismissButton = { TextButton(onClick = { pendingConfirm = null }) { Text("Cancel") } }
        )
    }

    languagePicker?.let { kind ->
        val current = if (kind == "audio") prefs.preferredAudioLanguage else prefs.preferredSubtitleLanguage
        AlertDialog(
            onDismissRequest = { languagePicker = null },
            title = { Text(if (kind == "audio") "Preferred audio language" else "Preferred subtitle language") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(LANGUAGE_OPTIONS, key = { it.first }) { (code, name) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (kind == "audio") actions.setPreferredAudioLanguage(code)
                                    else actions.setPreferredSubtitleLanguage(code)
                                    languagePicker = null
                                }
                        ) {
                            RadioButton(selected = current == code, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(name, modifier = Modifier.padding(vertical = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { languagePicker = null }) { Text("Close") } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header") {
            ScreenHeader(title = "Settings", startPadding = 0.dp)
        }

        state.statusMessage?.let { msg ->
            item(key = "status") {
                StatusMessageCard(message = msg, isError = state.isError, onDismiss = actions::dismissStatus)
            }
        }

        item(key = "tmdb") {
            SectionCard(
                title = "TMDB (optional)",
                subtitle = if (state.isTmdbSaved) "Connected. Catalogs and search use TMDB."
                else "Without a key Argon uses the free Cinemeta catalog. A free TMDB key adds trending lists and cast photos."
            ) {
                if (state.isTmdbSaved) {
                    ConnectedRow("TMDB key saved on this device")
                }
                OutlinedTextField(
                    value = state.tmdbKeyInput,
                    onValueChange = actions::onTmdbKeyChange,
                    label = { Text(if (state.isTmdbSaved) "Replace key" else "API key or Read Access Token") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = actions::saveTmdbKey,
                        enabled = state.tmdbKeyInput.isNotBlank() && !state.isSavingTmdb
                    ) {
                        if (state.isSavingTmdb) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Verify & save")
                    }
                    if (state.isTmdbSaved) {
                        OutlinedButton(onClick = actions::removeTmdbKey) { Text("Remove") }
                    }
                }
            }
        }

        item(key = "torbox") {
            SectionCard(
                title = "TorBox",
                subtitle = "Resolves torrent streams into direct links. Key from torbox.app/settings."
            ) {
                state.torBoxAccount?.let { acc ->
                    ConnectedRow("${acc.email} · ${acc.planTier.displayName}")
                }
                if (state.isTorBoxSaved && state.torBoxAccount == null && !state.isTestingTorBox) {
                    Text(
                        text = "A key is saved but couldn't be verified. Tap Test.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                OutlinedTextField(
                    value = state.torBoxKeyInput,
                    onValueChange = actions::onTorBoxKeyChange,
                    label = { Text(if (state.isTorBoxSaved) "Replace API key" else "API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = actions::saveAndTestTorBoxKey,
                        enabled = state.torBoxKeyInput.isNotBlank() && !state.isTestingTorBox
                    ) { Text("Save & connect") }
                    if (state.isTorBoxSaved) {
                        OutlinedButton(
                            onClick = { actions.testTorBoxConnection() },
                            enabled = !state.isTestingTorBox
                        ) {
                            if (state.isTestingTorBox) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Test")
                        }
                        OutlinedButton(onClick = actions::disconnectTorBox) { Text("Disconnect") }
                    }
                }
            }
        }

        item(key = "playback") {
            SectionCard(title = "Playback") {
                Text("Open streams with", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.availablePlayers.forEach { target ->
                        FilterChip(
                            selected = prefs.defaultPlayer == target,
                            onClick = { actions.setDefaultPlayer(target) },
                            label = { Text(playerLabel(target)) }
                        )
                    }
                }
                SwitchRow(
                    title = "Subtitles off by default",
                    checked = prefs.subtitlesDisabledByDefault,
                    onCheckedChange = actions::setSubtitlesDisabledByDefault
                )
                ValueRow(
                    title = "Audio language",
                    value = languageName(prefs.preferredAudioLanguage),
                    onClick = { languagePicker = "audio" }
                )
                ValueRow(
                    title = "Subtitle language",
                    value = languageName(prefs.preferredSubtitleLanguage),
                    onClick = { languagePicker = "subtitle" }
                )
            }
        }

        item(key = "streams") {
            SectionCard(title = "Streams") {
                Text("Maximum quality", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("2160p" to "4K", "1080p" to "1080p", "720p" to "720p").forEach { (res, label) ->
                        FilterChip(
                            selected = prefs.preferredMaxResolution == res,
                            onClick = { actions.setMaxResolution(res) },
                            label = { Text(label) }
                        )
                    }
                }
                SwitchRow(
                    title = "Show instant (cached) streams first",
                    checked = prefs.preferCachedDebridStreams,
                    onCheckedChange = actions::setPreferCached
                )
            }
        }

        item(key = "appearance") {
            SectionCard(title = "Appearance") {
                SwitchRow(title = "Dark theme", checked = prefs.darkTheme, onCheckedChange = actions::setDarkTheme)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchRow(
                        title = "Use wallpaper colors",
                        checked = prefs.dynamicColors,
                        onCheckedChange = actions::setDynamicColors
                    )
                }
            }
        }

        item(key = "backup") {
            SectionCard(
                title = "Backup & restore",
                subtitle = "Saves your settings and add-ons to a file. API keys are not included."
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { backupSaver.launch("argon-backup.json") }) { Text("Save backup") }
                    OutlinedButton(onClick = { backupPicker.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Restore") }
                }
            }
        }

        state.crashLog?.let { log ->
            item(key = "crash") {
                SectionCard(
                    title = "Crash report",
                    subtitle = "The app crashed recently. Share this with the developer to help fix it."
                ) {
                    Text(
                        text = log.lineSequence().take(4).joinToString(separator = System.lineSeparator()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Argon crash report")
                                putExtra(Intent.EXTRA_TEXT, log)
                            }
                            context.startActivity(Intent.createChooser(send, "Share crash report"))
                        }) { Text("Share") }
                        OutlinedButton(onClick = actions::clearCrashLog) { Text("Clear") }
                    }
                }
            }
        }

        item(key = "data") {
            SectionCard(title = "Data") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = actions::clearImageCache) { Text("Clear image cache") }
                    OutlinedButton(onClick = { pendingConfirm = ConfirmAction.CLEAR_HISTORY }) { Text("Clear history") }
                }
                Button(
                    onClick = { pendingConfirm = ConfirmAction.WIPE_ALL },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Wipe all data & keys") }
            }
        }

        item(key = "diagnostics") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showDiagnostics = !showDiagnostics
                                if (showDiagnostics) actions.refreshDiagnostics()
                            }
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Network diagnostics", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Recent requests with keys and tokens redacted",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            if (showDiagnostics) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }
                    if (showDiagnostics) {
                        val logs = state.recentDiagnostics.take(30)
                        if (logs.isEmpty()) {
                            Text(
                                "No requests yet.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                            )
                        }
                        logs.forEach { log ->
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(
                                    text = "${log.method} · ${log.statusCode ?: log.errorSummary ?: "ERR"} · ${log.durationMs} ms",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (log.statusCode in 200..299) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = log.sanitizedUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "about") {
            Text(
                text = "Argon ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}). Metadata from Cinemeta and TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun ConnectedRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

private fun playerLabel(target: ExternalPlayerTarget): String = when (target) {
    ExternalPlayerTarget.INTERNAL_EXOPLAYER -> "Built-in player"
    ExternalPlayerTarget.SYSTEM_CHOOSER -> "Ask every time"
    else -> target.displayName
}

private fun languageName(code: String): String =
    LANGUAGE_OPTIONS.firstOrNull { it.first == code }?.second ?: code.uppercase()
