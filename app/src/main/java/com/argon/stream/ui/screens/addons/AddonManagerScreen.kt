package com.argon.stream.ui.screens.addons

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.argon.stream.domain.model.ConfigImportPreview
import com.argon.stream.domain.model.InstalledAddon
import com.argon.stream.ui.components.ScreenHeader
import com.argon.stream.ui.components.SectionCard
import com.argon.stream.ui.components.StatusMessageCard

/** UI actions, implemented by [AddonManagerViewModel]; previews and screenshot tests can pass a no-op object. */
interface AddonManagerActions {
    fun dismissStatus() {}
    fun onManifestUrlChange(url: String) {}
    fun onJsonInputChange(jsonText: String) {}
    fun loadJsonFromUri(uri: Uri) {}
    fun installFromUrl() {}
    fun applyPreviewedImport() {}
    fun toggleEnabled(addon: InstalledAddon, enabled: Boolean) {}
    fun moveAddon(addonId: String, moveUp: Boolean) {}
    fun testAddonConnection(addon: InstalledAddon) {}
    fun removeAddon(addon: InstalledAddon) {}
    fun writeBackupTo(uri: Uri) {}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddonManagerScreen(
    viewModel: AddonManagerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AddonManagerContent(state = state, actions = viewModel)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddonManagerContent(
    state: AddonManagerUiState,
    actions: AddonManagerActions
) {
    var addonPendingRemoval by remember { mutableStateOf<InstalledAddon?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(actions::loadJsonFromUri)
    }
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let(actions::writeBackupTo)
    }

    addonPendingRemoval?.let { addon ->
        AlertDialog(
            onDismissRequest = { addonPendingRemoval = null },
            title = { Text("Remove ${addon.name}?") },
            text = { Text("You can reinstall it later from its manifest URL.") },
            confirmButton = {
                TextButton(onClick = {
                    actions.removeAddon(addon)
                    addonPendingRemoval = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { addonPendingRemoval = null }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header") {
            ScreenHeader(
                title = "Add-ons",
                subtitle = "Stremio-compatible add-ons provide streams and subtitles",
                startPadding = 0.dp
            )
        }

        state.statusMessage?.let { msg ->
            item(key = "status") {
                StatusMessageCard(message = msg, isError = state.isError, onDismiss = actions::dismissStatus)
            }
        }

        item(key = "install") {
            SectionCard(
                title = "Install an add-on",
                subtitle = "Paste a manifest URL (https://…/manifest.json or stremio://…)"
            ) {
                OutlinedTextField(
                    value = state.manifestUrlInput,
                    onValueChange = actions::onManifestUrlChange,
                    placeholder = { Text("https://…/manifest.json") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { actions.installFromUrl() }),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = actions::installFromUrl,
                    enabled = !state.isInstalling && state.manifestUrlInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isInstalling) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Checking manifest…")
                    } else {
                        Text("Install")
                    }
                }
            }
        }

        item(key = "import") {
            SectionCard(
                title = "Import or back up",
                subtitle = "AIOStreams export, Stremio manifest JSON, or an Argon backup"
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import file")
                    }
                    OutlinedButton(onClick = { backupLauncher.launch("argon-addons-backup.json") }) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save backup")
                    }
                }
                OutlinedTextField(
                    value = state.jsonImportInput,
                    onValueChange = actions::onJsonInputChange,
                    placeholder = { Text("…or paste JSON here") },
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                state.importPreview?.let { preview ->
                    ImportPreviewCard(preview = preview, isBusy = state.isInstalling, onApply = actions::applyPreviewedImport)
                }
            }
        }

        item(key = "installed_title") {
            Text(
                text = "Installed (${state.addons.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(state.addons, key = { it.id }) { addon ->
            InstalledAddonCard(
                addon = addon,
                isTesting = state.testingAddonId == addon.id,
                isFirst = addon == state.addons.firstOrNull(),
                isLast = addon == state.addons.lastOrNull(),
                onToggle = { actions.toggleEnabled(addon, it) },
                onMoveUp = { actions.moveAddon(addon.id, true) },
                onMoveDown = { actions.moveAddon(addon.id, false) },
                onTest = { actions.testAddonConnection(addon) },
                onRemove = { addonPendingRemoval = addon }
            )
        }
    }
}

@Composable
private fun ImportPreviewCard(
    preview: ConfigImportPreview,
    isBusy: Boolean,
    onApply: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val formatLabel = when (preview.formatType) {
                "AIOSTREAMS_EXPORT" -> "AIOStreams export"
                "ARGON_BACKUP" -> "Argon backup"
                "STREMIO_MANIFEST" -> "Stremio manifest"
                "INVALID_JSON" -> "Invalid JSON"
                else -> "Unrecognized format"
            }
            Text(formatLabel, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                text = "${preview.discoveredManifestUrls.size} add-on URL(s) found",
                style = MaterialTheme.typography.bodyMedium
            )
            preview.requiredServices.forEach { svc ->
                Text(
                    text = "• ${svc.displayName}" + if (svc.isConfiguredInArgon) " (connected)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            preview.warnings.forEach { warning ->
                Text(
                    text = warning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (preview.discoveredManifestUrls.isNotEmpty()) {
                Button(onClick = onApply, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                    Text("Install ${preview.discoveredManifestUrls.size} add-on(s)")
                }
            }
        }
    }
}

@Composable
private fun InstalledAddonCard(
    addon: InstalledAddon,
    isTesting: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onTest: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = addon.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "v${addon.version} · ${addon.resources.joinToString(", ")}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = addon.lastHealthStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (addon.lastHealthStatus.startsWith("ERROR")) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(checked = addon.isEnabled, onCheckedChange = onToggle)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMoveUp, enabled = !isFirst) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move up")
                }
                IconButton(onClick = onMoveDown, enabled = !isLast) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move down")
                }
                IconButton(onClick = onTest, enabled = !isTesting) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.NetworkCheck, contentDescription = "Test connection")
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
