package com.argon.stream.ui.screens.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argon.stream.data.repository.Profile
import kotlinx.coroutines.launch

val PROFILE_COLORS = listOf(
    Color(0xFF00B8D4), Color(0xFFFF6D00), Color(0xFF7C4DFF), Color(0xFF00C853),
    Color(0xFFFF4081), Color(0xFFFFC400), Color(0xFF2979FF), Color(0xFFD50000)
)

fun profileColor(index: Int): Color = PROFILE_COLORS[index.mod(PROFILE_COLORS.size)]

/** Round avatar with the profile's initial. */
@Composable
fun ProfileAvatar(name: String, colorIndex: Int, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(profileColor(colorIndex)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.trim().take(1).uppercase().ifBlank { "?" },
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhoIsWatchingScreen(
    state: ProfilesUiState,
    actions: ProfileActions
) {
    var managing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pinFor by remember { mutableStateOf<Profile?>(null) }

    BackHandler(enabled = managing || state.canClose) {
        if (managing) managing = false else actions.close()
    }

    pinFor?.let { profile ->
        PinDialog(profile = profile, actions = actions, onDismiss = { pinFor = null })
    }
    if (creating) {
        ProfileEditDialog(
            profile = null,
            canDelete = false,
            onDismiss = { creating = false },
            onSave = { name, color, pin, _ ->
                actions.create(name, color, pin)
                creating = false
            },
            onDelete = {}
        )
    }
    editing?.let { profile ->
        ProfileEditDialog(
            profile = profile,
            canDelete = state.profiles.size > 1,
            onDismiss = { editing = null },
            onSave = { name, color, pin, removePin ->
                actions.update(profile, name, color)
                if (removePin) actions.setPin(profile, null) else if (!pin.isNullOrBlank()) actions.setPin(profile, pin)
                editing = null
            },
            onDelete = {
                actions.delete(profile)
                editing = null
            }
        )
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            if (state.canClose && !managing) {
                IconButton(onClick = actions::close, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (managing) "Manage profiles" else "Who's watching?",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(28.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    state.profiles.forEach { profile ->
                        ProfileTile(
                            label = profile.name,
                            isActive = profile.id == state.activeProfileId && state.canClose,
                            showLock = profile.hasPin && !managing,
                            showEdit = managing,
                            onClick = {
                                when {
                                    managing -> editing = profile
                                    profile.hasPin -> pinFor = profile
                                    else -> actions.select(profile)
                                }
                            }
                        ) { ProfileAvatar(profile.name, profile.colorIndex, 88.dp) }
                    }
                    if (state.profiles.size < MAX_PROFILES) {
                        ProfileTile(label = "Add profile", isActive = false, showLock = false, showEdit = false, onClick = { creating = true }) {
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(36.dp))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                OutlinedButton(onClick = { managing = !managing }) {
                    Text(if (managing) "Done" else "Manage profiles")
                }
            }
        }
    }
}

@Composable
private fun ProfileTile(
    label: String,
    isActive: Boolean,
    showLock: Boolean,
    showEdit: Boolean,
    onClick: () -> Unit,
    avatar: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(104.dp)
            .clip(MaterialTheme.shapes.medium)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier.border(
                width = 3.dp,
                color = if (focused || isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape
            ).padding(4.dp)
        ) {
            avatar()
            if (showLock || showEdit) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.align(Alignment.BottomEnd).size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (showEdit) Icons.Default.Edit else Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PinDialog(profile: Profile, actions: ProfileActions, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { ProfileAvatar(profile.name, profile.colorIndex, 48.dp) },
        title = { Text("Enter PIN for ${profile.name}") },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { value -> pin = value.filter { it.isDigit() }.take(8); wrong = false },
                label = { Text("PIN") },
                isError = wrong,
                supportingText = if (wrong) { { Text("Wrong PIN") } } else null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                enabled = pin.length >= 4,
                onClick = {
                    scope.launch {
                        if (actions.verifyPin(profile, pin)) {
                            actions.select(profile)
                            onDismiss()
                        } else {
                            wrong = true
                            pin = ""
                        }
                    }
                }
            ) { Text("Unlock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditDialog(
    profile: Profile?,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, colorIndex: Int, pin: String?, removePin: Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(profile?.name.orEmpty()) }
    var colorIndex by remember { mutableIntStateOf(profile?.colorIndex ?: 1) }
    var pin by remember { mutableStateOf("") }
    var removePin by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete && profile != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${profile.name}?") },
            text = { Text("Their watchlist, favorites, history and watched marks are deleted from this device.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (profile == null) "Add profile" else "Edit profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(name.ifBlank { "?" }, colorIndex, 48.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(20) },
                        label = { Text("Name") },
                        singleLine = true
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PROFILE_COLORS.indices.forEach { index ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(profileColor(index))
                                .border(3.dp, if (index == colorIndex) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                                .clickable { colorIndex = index }
                        )
                    }
                }
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value -> pin = value.filter { it.isDigit() }.take(8); removePin = false },
                    label = { Text(if (profile?.hasPin == true) "New PIN (optional)" else "PIN (optional, 4–8 digits)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
                if (profile?.hasPin == true) {
                    TextButton(onClick = { removePin = !removePin; pin = "" }) {
                        Text(if (removePin) "PIN will be removed" else "Remove PIN")
                    }
                }
                if (profile != null && canDelete) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete profile", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && (pin.isEmpty() || pin.length >= 4),
                onClick = { onSave(name, colorIndex, pin.ifBlank { null }, removePin) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private const val MAX_PROFILES = 6
