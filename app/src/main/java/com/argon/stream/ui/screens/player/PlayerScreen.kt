package com.argon.stream.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import androidx.activity.ComponentActivity
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.argon.stream.core.player.PictureInPictureState
import com.argon.stream.core.player.buildPipParams
import com.argon.stream.core.player.pipAspectRatio
import com.argon.stream.core.player.supportsPictureInPicture
import com.argon.stream.core.player.tryEnterPictureInPicture
import com.argon.stream.domain.model.ExternalPlayerTarget
import com.argon.stream.domain.model.SelectableTrackInfo
import kotlinx.coroutines.delay
import java.util.Locale

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

internal enum class FitMode(val label: String, val resizeMode: Int) {
    FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    ZOOM("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    STRETCH("Stretch", AspectRatioFrameLayout.RESIZE_MODE_FILL)
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val lifecycleOwner = LocalLifecycleOwner.current

    var showControls by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var showTrackDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showExternalPlayerDialog by remember { mutableStateOf(false) }
    var gestureHudText by remember { mutableStateOf<String?>(null) }
    var hudTick by remember { mutableIntStateOf(0) }
    var isLandscapeLocked by remember { mutableStateOf(true) }
    var fitMode by remember { mutableStateOf(FitMode.FIT) }
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    var volumeDragAccumulator by remember { mutableFloatStateOf(0f) }
    var subtitleCues by remember { mutableStateOf<List<Cue>>(emptyList()) }

    // Subtitles are drawn in their own full-screen layer so zoom/stretch can't push them off screen.
    DisposableEffect(viewModel.exoPlayer) {
        val player = viewModel.exoPlayer
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                subtitleCues = cueGroup.cues
            }
        }
        player.addListener(listener)
        subtitleCues = player.currentCues.cues
        onDispose { player.removeListener(listener) }
    }

    fun showHud(text: String) {
        gestureHudText = text
        hudTick++
    }

    fun registerInteraction() {
        interactionTick++
    }

    val subtitlePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.attachCustomSubtitleUri(uri, uri.lastPathSegment?.substringAfterLast('/') ?: "subtitle.srt")
        }
    }

    // Immersive full screen, landscape, keep screen on; everything is restored when leaving the player.
    DisposableEffect(activity) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.attributes = window?.attributes?.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Picture-in-picture: track the mode, and let Android enter it automatically while playing.
    val pipSupported = remember(context) { context.supportsPictureInPicture() }
    var isInPip by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }
    DisposableEffect(activity) {
        val componentActivity = activity as? ComponentActivity
        val listener = Consumer<PictureInPictureModeChangedInfo> { isInPip = it.isInPictureInPictureMode }
        componentActivity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            componentActivity?.removeOnPictureInPictureModeChangedListener(listener)
            PictureInPictureState.autoEnterEnabled = false
            if (pipSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { activity?.setPictureInPictureParams(buildPipParams(autoEnter = false)) }
            }
        }
    }
    LaunchedEffect(state.isPlaying, state.videoAspectRatio, pipSupported) {
        if (!pipSupported) return@LaunchedEffect
        PictureInPictureState.aspectRatio = pipAspectRatio(state.videoAspectRatio)
        PictureInPictureState.autoEnterEnabled = state.isPlaying
        runCatching { activity?.setPictureInPictureParams(buildPipParams(autoEnter = state.isPlaying)) }
    }

    // Pause (and save progress) when the app goes to the background.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.pause()
                viewModel.persistProgressNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Auto-hide controls while playing.
    LaunchedEffect(showControls, state.isPlaying, interactionTick, scrubFraction) {
        if (showControls && state.isPlaying && scrubFraction == null) {
            delay(3_500)
            showControls = false
        }
    }

    LaunchedEffect(hudTick) {
        if (gestureHudText != null) {
            delay(900)
            gestureHudText = null
        }
    }

    if (showTrackDialog) {
        TrackAndSubtitleDialog(
            state = state,
            onDismiss = { showTrackDialog = false },
            onSelectAudio = {
                viewModel.selectAudioTrack(it)
                showTrackDialog = false
            },
            onSelectSubtitle = {
                viewModel.selectSubtitleTrack(it)
                showTrackDialog = false
            },
            onLoadLocalSubtitle = {
                showTrackDialog = false
                subtitlePickerLauncher.launch(arrayOf("application/x-subrip", "text/vtt", "text/plain", "application/octet-stream", "*/*"))
            },
            onCycleSubtitleScale = viewModel::cycleSubtitleScale
        )
    }

    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback speed") },
            text = {
                Column {
                    SPEED_OPTIONS.forEach { speed ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setPlaybackSpeed(speed)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(formatSpeed(speed), modifier = Modifier.weight(1f))
                            if (state.playbackSpeed == speed) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSpeedDialog = false }) { Text("Close") } }
        )
    }

    if (showExternalPlayerDialog) {
        ExternalPlayerHandoffDialog(
            players = state.availableExternalPlayers,
            onDismiss = { showExternalPlayerDialog = false },
            onSelectExternal = { target ->
                showExternalPlayerDialog = false
                viewModel.handoffToExternalPlayer(target)
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                // Two fingers: pinch out = zoom to fill, pinch in = fit, two-finger tap = toggle.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var startDistance = 0f
                    var multiTouch = false
                    var scaled = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            multiTouch = true
                            val distance = (pressed[0].position - pressed[1].position).getDistance()
                            if (startDistance == 0f) startDistance = distance
                            if (!scaled && startDistance > 0f) {
                                val ratio = distance / startDistance
                                if (ratio > 1.12f) {
                                    fitMode = FitMode.ZOOM
                                    showHud("Zoom to fill")
                                    scaled = true
                                } else if (ratio < 0.89f) {
                                    fitMode = FitMode.FIT
                                    showHud("Fit to screen")
                                    scaled = true
                                }
                            }
                        }
                        if (multiTouch) event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                    if (multiTouch && !scaled) {
                        fitMode = if (fitMode == FitMode.FIT) FitMode.ZOOM else FitMode.FIT
                        showHud(if (fitMode == FitMode.FIT) "Fit to screen" else "Zoom to fill")
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                        registerInteraction()
                    },
                    onDoubleTap = { offset ->
                        val third = size.width / 3f
                        when {
                            offset.x < third -> {
                                viewModel.seekRelative(-PlayerViewModel.SEEK_STEP_MS)
                                showHud("−10s")
                            }
                            offset.x > third * 2 -> {
                                viewModel.seekRelative(PlayerViewModel.SEEK_STEP_MS)
                                showHud("+10s")
                            }
                            else -> viewModel.togglePlayPause()
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { volumeDragAccumulator = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        val isLeftSide = change.position.x < size.width / 2f
                        if (isLeftSide) {
                            val window = activity?.window ?: return@detectVerticalDragGestures
                            val lp = window.attributes
                            val current = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                            val updated = (current - dragAmount / size.height).coerceIn(0.02f, 1.0f)
                            lp.screenBrightness = updated
                            window.attributes = lp
                            showHud("Brightness ${(updated * 100).toInt()}%")
                        } else {
                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                            volumeDragAccumulator -= dragAmount
                            val stepPx = size.height / (maxVol * 1.5f)
                            if (kotlin.math.abs(volumeDragAccumulator) >= stepPx) {
                                val delta = if (volumeDragAccumulator > 0) 1 else -1
                                volumeDragAccumulator = 0f
                                val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val nextVol = (curVol + delta).coerceIn(0, maxVol)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVol, 0)
                                showHud("Volume ${(nextVol * 100) / maxVol}%")
                            }
                        }
                    }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = viewModel.exoPlayer
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    // The built-in subtitle view lives inside the zoomed video frame; ours is screen-sized.
                    subtitleView?.visibility = android.view.View.GONE
                }
            },
            update = { playerView -> playerView.resizeMode = fitMode.resizeMode },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )

        AndroidView(
            factory = { ctx ->
                SubtitleView(ctx).apply {
                    setStyle(
                        CaptionStyleCompat(
                            android.graphics.Color.WHITE,
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null
                        )
                    )
                    setBottomPaddingFraction(SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION)
                }
            },
            update = { view ->
                view.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, state.subtitleScale.fontSizeSp)
                view.setCues(if (state.subtitlesEnabled) subtitleCues else emptyList())
            },
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(horizontal = 12.dp)
        )

        if (state.isBuffering && state.playbackError == null) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        gestureHudText?.takeIf { !isInPip }?.let { hud ->
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
            ) {
                Text(
                    text = hud,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = !isInPip && (showControls || state.playbackError != null || !state.isPlaying && !state.isBuffering),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            PlayerControlsOverlay(
                state = state,
                fitMode = fitMode,
                scrubFraction = scrubFraction,
                onBack = onBack,
                onTracks = { showTrackDialog = true },
                onSpeed = { showSpeedDialog = true },
                onExternal = { showExternalPlayerDialog = true },
                onCycleFit = {
                    fitMode = FitMode.entries[(fitMode.ordinal + 1) % FitMode.entries.size]
                    showHud(fitMode.label)
                    registerInteraction()
                },
                onRotate = {
                    isLandscapeLocked = !isLandscapeLocked
                    activity?.requestedOrientation = if (isLandscapeLocked) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                    registerInteraction()
                },
                onTogglePlay = {
                    viewModel.togglePlayPause()
                    registerInteraction()
                },
                onSeekBack = {
                    viewModel.seekRelative(-PlayerViewModel.SEEK_STEP_MS)
                    registerInteraction()
                },
                onSeekForward = {
                    viewModel.seekRelative(PlayerViewModel.SEEK_STEP_MS)
                    registerInteraction()
                },
                onScrub = { scrubFraction = it },
                onScrubFinished = {
                    scrubFraction?.let { fraction -> viewModel.seekTo((state.durationMs * fraction).toLong()) }
                    scrubFraction = null
                },
                onRetry = viewModel::retryPlayback,
                onEnterPip = if (pipSupported) {
                    { activity?.tryEnterPictureInPicture() }
                } else null
            )
        }
    }
}

@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerControlsOverlay(
    state: PlayerUiState,
    fitMode: FitMode,
    scrubFraction: Float?,
    onBack: () -> Unit,
    onTracks: () -> Unit,
    onSpeed: () -> Unit,
    onExternal: () -> Unit,
    onCycleFit: () -> Unit,
    onRotate: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    onRetry: () -> Unit,
    onEnterPip: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.systemBars))
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                state.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1
                    )
                }
            }
            IconButton(onClick = onTracks) {
                Icon(Icons.Default.ClosedCaption, contentDescription = "Audio and subtitles", tint = Color.White)
            }
            IconButton(onClick = onSpeed) {
                Icon(Icons.Default.Speed, contentDescription = "Playback speed", tint = Color.White)
            }
            IconButton(onClick = onCycleFit) {
                Icon(Icons.Default.AspectRatio, contentDescription = "Video size: ${fitMode.label}", tint = Color.White)
            }
            if (onEnterPip != null) {
                IconButton(onClick = onEnterPip) {
                    Icon(Icons.Default.PictureInPictureAlt, contentDescription = "Picture in picture", tint = Color.White)
                }
            }
            IconButton(onClick = onRotate) {
                Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate", tint = Color.White)
            }
            if (state.availableExternalPlayers.any { it != ExternalPlayerTarget.INTERNAL_EXOPLAYER }) {
                IconButton(onClick = onExternal) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in another player", tint = Color.White)
                }
            }
        }

        val error = state.playbackError
        if (error != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp, vertical = 56.dp)
                    .widthIn(max = 480.dp)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Playback problem", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                    Text(error.userSummary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = error.technicalDetail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = onRetry) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry")
                        }
                        if (state.availableExternalPlayers.any { it != ExternalPlayerTarget.INTERNAL_EXOPLAYER }) {
                            FilledTonalButton(onClick = onExternal) { Text("Other player") }
                        }
                        OutlinedButton(onClick = onBack) { Text("Back") }
                    }
                }
            }
        } else {
            // Center transport controls
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSeekBack, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.Replay10, contentDescription = "Back 10 seconds", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.size(72.dp)
                ) {
                    IconButton(onClick = onTogglePlay) {
                        Icon(
                            imageVector = when {
                                state.isEnded -> Icons.Default.Replay
                                state.isPlaying -> Icons.Default.Pause
                                else -> Icons.Default.PlayArrow
                            },
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
                IconButton(onClick = onSeekForward, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.Forward10, contentDescription = "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }
        }

        // Bottom seek bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            val duration = state.durationMs
            val playedFraction = if (duration > 0L) (state.currentPositionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
            val shownFraction = scrubFraction ?: playedFraction
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDurationMs((shownFraction * duration).toLong()),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
                Slider(
                    value = shownFraction,
                    onValueChange = onScrub,
                    onValueChangeFinished = onScrubFinished,
                    enabled = duration > 0L,
                    colors = SliderDefaults.colors(
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )
                Text(
                    text = if (duration > 0L) formatDurationMs(duration) else "--:--",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
            }
            if (state.playbackSpeed != 1.0f) {
                Text(
                    text = "Speed ${formatSpeed(state.playbackSpeed)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun TrackAndSubtitleDialog(
    state: PlayerUiState,
    onDismiss: () -> Unit,
    onSelectAudio: (SelectableTrackInfo) -> Unit,
    onSelectSubtitle: (SelectableTrackInfo?) -> Unit,
    onLoadLocalSubtitle: () -> Unit,
    onCycleSubtitleScale: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio & subtitles") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                item { SectionLabel("Audio") }
                if (state.audioTracks.isEmpty()) {
                    item { HintText("No alternative audio tracks.") }
                }
                items(state.audioTracks, key = { "a_${it.id}" }) { track ->
                    TrackRow(label = track.label, selected = track.isSelected, onClick = { onSelectAudio(track) })
                }

                item { SectionLabel("Subtitles") }
                item {
                    TrackRow(label = "Off", selected = !state.subtitlesEnabled, onClick = { onSelectSubtitle(null) })
                }
                items(state.subtitleTracks, key = { "s_${it.id}" }) { sub ->
                    TrackRow(
                        label = sub.label,
                        selected = sub.isSelected && state.subtitlesEnabled,
                        onClick = { onSelectSubtitle(sub) }
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = onLoadLocalSubtitle) { Text("Load file") }
                        OutlinedButton(onClick = onCycleSubtitleScale) { Text("Size: ${state.subtitleScale.label}") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun HintText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ExternalPlayerHandoffDialog(
    players: List<ExternalPlayerTarget>,
    onDismiss: () -> Unit,
    onSelectExternal: (ExternalPlayerTarget) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open in another player") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HintText("Other players can't report your progress back to Argon.")
                players.filter { it != ExternalPlayerTarget.INTERNAL_EXOPLAYER }.forEach { target ->
                    TrackRow(
                        label = if (target == ExternalPlayerTarget.SYSTEM_CHOOSER) "Choose an app…" else target.displayName,
                        selected = false,
                        onClick = { onSelectExternal(target) }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatSpeed(speed: Float): String =
    if (speed == 1.0f) "Normal" else String.format(Locale.US, "%sx", speed.toString().removeSuffix(".0"))

private fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
