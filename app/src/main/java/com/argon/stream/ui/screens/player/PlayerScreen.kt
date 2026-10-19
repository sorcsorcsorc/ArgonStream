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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import com.argon.stream.core.player.SkipSegment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import com.argon.stream.domain.model.MediaType
import com.argon.stream.ui.components.LocalIsTv
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
    onOpenStreams: (type: MediaType, mediaId: String, videoId: String, title: String) -> Unit = { _, _, _, _ -> },
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val previewFrame by viewModel.seekPreview.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val lifecycleOwner = LocalLifecycleOwner.current
    val isTv = LocalIsTv.current
    val rootFocus = remember { FocusRequester() }
    val playButtonFocus = remember { FocusRequester() }
    val seekBarFocus = remember { FocusRequester() }
    val skipButtonFocus = remember { FocusRequester() }

    var showControls by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var showTrackDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showExternalPlayerDialog by remember { mutableStateOf(false) }
    var showEpisodes by remember { mutableStateOf(false) }
    var gestureHudText by remember { mutableStateOf<String?>(null) }
    var hudTick by remember { mutableIntStateOf(0) }
    var isLandscapeLocked by remember { mutableStateOf(true) }
    var fitMode by remember { mutableStateOf(FitMode.FIT) }
    var scrubPositionMs by remember { mutableStateOf<Long?>(null) }
    /** True while the remote's Left/Right keys move the target; the jump happens after a short pause. */
    var keyScrubbing by remember { mutableStateOf(false) }
    var keyScrubTick by remember { mutableIntStateOf(0) }
    var seekAccumMs by remember { mutableStateOf(0L) }
    var seekAccumTick by remember { mutableIntStateOf(0) }
    var volumeDragAccumulator by remember { mutableFloatStateOf(0f) }
    var subtitleCues by remember { mutableStateOf<List<Cue>>(emptyList()) }
    var isLocked by remember { mutableStateOf(false) }
    var lockHintTick by remember { mutableIntStateOf(0) }
    var lockHintVisible by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }

    fun showLockHint() {
        lockHintVisible = true
        lockHintTick++
    }

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

    /** Quick skips (double tap, 10s buttons, media keys) add up into one "+30s" label and open the bar. */
    fun quickSeek(deltaMs: Long) {
        viewModel.seekRelative(deltaMs)
        seekAccumMs = if (seekAccumMs != 0L && (seekAccumMs > 0) == (deltaMs > 0)) seekAccumMs + deltaMs else deltaMs
        seekAccumTick++
        showControls = true
        registerInteraction()
    }

    /** TV remote skipping: move the target and show the preview; the jump happens when the keys rest. */
    fun keyScrub(deltaMs: Long) {
        val duration = state.durationMs
        if (duration <= 0L) return
        val base = scrubPositionMs ?: state.currentPositionMs
        scrubPositionMs = (base + deltaMs).coerceIn(0L, duration)
        keyScrubbing = true
        keyScrubTick++
        showControls = true
        registerInteraction()
    }

    fun commitScrub() {
        scrubPositionMs?.let { viewModel.seekTo(it) }
        scrubPositionMs = null
        keyScrubbing = false
        registerInteraction()
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

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PlayerEvent.OpenStreamPicker -> onOpenStreams(event.mediaType, event.mediaId, event.videoId, event.title)
            }
        }
    }

    // Skip intro / recap: shown while playback is inside that part of the episode.
    val position = state.currentPositionMs
    val skipTarget: Pair<String, Long>? = run {
        val segments = state.skipSegments
        fun SkipSegment.contains(ms: Long) = ms >= startMs && ms < (endMs ?: Long.MAX_VALUE) - 1_000L
        when {
            state.durationMs <= 0L || state.playbackError != null -> null
            segments.intro?.contains(position) == true -> "Skip intro" to (segments.intro.endMs ?: return@run null)
            segments.recap?.contains(position) == true -> "Skip recap" to (segments.recap.endMs ?: return@run null)
            else -> null
        }
    }

    // TV remote: when controls hide, focus returns to the player so D-pad keys seek / play-pause.
    LaunchedEffect(showControls, isTv) {
        if (!showControls) runCatching { rootFocus.requestFocus() }
        else if (isTv && !keyScrubbing) runCatching { playButtonFocus.requestFocus() }
    }
    LaunchedEffect(keyScrubbing) {
        if (!keyScrubbing) return@LaunchedEffect
        // The bar may only appear on the next frame; try again briefly until it can take focus.
        repeat(5) {
            if (runCatching { seekBarFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(40)
        }
    }
    // The remote's skips are applied once the keys rest for a moment (OK applies them at once).
    LaunchedEffect(keyScrubTick) {
        if (!keyScrubbing) return@LaunchedEffect
        delay(1_100)
        commitScrub()
    }
    LaunchedEffect(scrubPositionMs) {
        viewModel.requestSeekPreview(scrubPositionMs)
    }
    LaunchedEffect(seekAccumTick) {
        if (seekAccumTick == 0) return@LaunchedEffect
        delay(1_000)
        seekAccumMs = 0L
    }
    BackHandler(enabled = showEpisodes) { showEpisodes = false }
    BackHandler(enabled = isTv && showControls && state.playbackError == null && !showEpisodes) {
        if (scrubPositionMs != null) {
            scrubPositionMs = null
            keyScrubbing = false
        } else {
            showControls = false
        }
    }
    // While the screen is locked, Back only reveals the unlock button.
    BackHandler(enabled = isLocked) { showLockHint() }
    LaunchedEffect(lockHintTick) {
        if (lockHintVisible) {
            delay(3_000)
            lockHintVisible = false
        }
    }

    if (showSleepDialog) {
        SleepTimerDialog(
            state = state,
            onDismiss = { showSleepDialog = false },
            onSelectMinutes = {
                viewModel.setSleepTimer(it)
                showSleepDialog = false
            },
            onEndOfEpisode = {
                viewModel.setSleepAtEndOfEpisode()
                showSleepDialog = false
            }
        )
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
    LaunchedEffect(showControls, state.isPlaying, interactionTick, scrubPositionMs, showEpisodes) {
        if (showControls && state.isPlaying && scrubPositionMs == null && !showEpisodes) {
            delay(if (isTv) 5_000 else 3_500)
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
            .focusRequester(rootFocus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        viewModel.togglePlayPause(); showControls = true; registerInteraction(); true
                    }
                    Key.MediaFastForward -> { keyScrub(PlayerViewModel.SEEK_STEP_MS); true }
                    Key.MediaRewind -> { keyScrub(-PlayerViewModel.SEEK_STEP_MS); true }
                    // While the remote is skipping, keep handling the keys here even before the bar has focus.
                    Key.DirectionLeft, Key.DirectionRight -> if (keyScrubbing) {
                        val repeat = event.nativeKeyEvent.repeatCount
                        val step = if (repeat >= 20) 60_000L else if (repeat >= 6) 30_000L else PlayerViewModel.SEEK_STEP_MS
                        keyScrub(if (event.key == Key.DirectionLeft) -step else step)
                        true
                    } else if (!showControls && !showEpisodes) {
                        keyScrub(if (event.key == Key.DirectionLeft) -PlayerViewModel.SEEK_STEP_MS else PlayerViewModel.SEEK_STEP_MS)
                        true
                    } else {
                        registerInteraction()
                        false
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> if (keyScrubbing) {
                        commitScrub(); true
                    } else if (!showControls && !showEpisodes) {
                        val skip = skipTarget
                        if (skip != null) viewModel.seekTo(skip.second)
                        else { viewModel.togglePlayPause(); showControls = true }
                        true
                    } else {
                        registerInteraction()
                        false
                    }
                    else -> if (!showControls && !showEpisodes) {
                        when (event.key) {
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                                val skip = skipTarget
                                if (skip != null) viewModel.seekTo(skip.second)
                                else { viewModel.togglePlayPause(); showControls = true }
                                true
                            }
                            // Like Netflix: Left/Right open the bar and move the target with a preview.
                            Key.DirectionLeft -> { keyScrub(-PlayerViewModel.SEEK_STEP_MS); true }
                            Key.DirectionRight -> { keyScrub(PlayerViewModel.SEEK_STEP_MS); true }
                            Key.DirectionUp, Key.DirectionDown -> { showControls = true; registerInteraction(); true }
                            else -> false
                        }
                    } else {
                        registerInteraction()
                        false
                    }
                }
            }
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
                        if (pressed.size >= 2 && !isLocked) {
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
                    onPress = {
                        // Hold anywhere for 2x speed (released = normal speed again).
                        if (isLocked) return@detectTapGestures
                        coroutineScope {
                            val boost = launch {
                                delay(LONG_PRESS_BOOST_MS)
                                viewModel.setSpeedBoost(true)
                            }
                            tryAwaitRelease()
                            boost.cancel()
                            viewModel.setSpeedBoost(false)
                        }
                    },
                    onLongPress = { /* handled in onPress; prevents a tap after the hold */ },
                    onTap = {
                        if (isLocked) {
                            showLockHint()
                            return@detectTapGestures
                        }
                        showControls = !showControls
                        registerInteraction()
                    },
                    onDoubleTap = { offset ->
                        if (isLocked) return@detectTapGestures
                        val third = size.width / 3f
                        when {
                            offset.x < third -> quickSeek(-PlayerViewModel.SEEK_STEP_MS)
                            offset.x > third * 2 -> quickSeek(PlayerViewModel.SEEK_STEP_MS)
                            else -> viewModel.togglePlayPause()
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { volumeDragAccumulator = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        if (isLocked) return@detectVerticalDragGestures
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

        val controlsShown = !isInPip && !isLocked &&
            (showControls || state.playbackError != null || !state.isPlaying && !state.isBuffering)
        if (state.isBuffering && state.playbackError == null && !controlsShown) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                strokeCap = StrokeCap.Round,
                modifier = Modifier.align(Alignment.Center).size(56.dp)
            )
        }

        gestureHudText?.takeIf { !isInPip }?.let { hud ->
            PlayerPill(text = hud, modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp))
        }

        if (state.isSpeedBoosted && !isInPip) {
            PlayerPill(text = "2x  ▶▶", modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp))
        }

        // "+30s" / "−20s" on the side that was double-tapped.
        if (seekAccumMs != 0L && !isInPip) {
            PlayerPill(
                text = formatSeekDelta(seekAccumMs),
                modifier = Modifier
                    .align(if (seekAccumMs > 0) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 64.dp)
            )
        }

        if (isLocked) {
            AnimatedVisibility(
                visible = lockHintVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.systemBars))
                    .padding(16.dp)
            ) {
                FilledTonalButton(onClick = {
                    isLocked = false
                    lockHintVisible = false
                    showControls = true
                }) {
                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tap to unlock")
                }
            }
        }

        val controlsVisible = controlsShown

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            PlayerControlsOverlay(
                state = state,
                fitMode = fitMode,
                scrubPositionMs = scrubPositionMs,
                previewFrame = previewFrame,
                onBack = onBack,
                onTracks = { showTrackDialog = true },
                onSpeed = { showSpeedDialog = true },
                onExternal = { showExternalPlayerDialog = true },
                onEpisodes = if (state.isSeries) {
                    {
                        viewModel.openEpisodesPanel()
                        showEpisodes = true
                    }
                } else null,
                onNextEpisode = if (state.nextEpisode != null) viewModel::playNextEpisode else null,
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
                onSeekBack = { quickSeek(-PlayerViewModel.SEEK_STEP_MS) },
                onSeekForward = { quickSeek(PlayerViewModel.SEEK_STEP_MS) },
                onScrub = {
                    keyScrubbing = false
                    scrubPositionMs = it
                    registerInteraction()
                },
                onScrubFinished = { commitScrub() },
                onKeyScrub = { keyScrub(it) },
                onRetry = viewModel::retryPlayback,
                playButtonFocus = playButtonFocus,
                seekBarFocus = seekBarFocus,
                showPhoneControls = !isTv,
                onLock = if (!isTv) {
                    {
                        isLocked = true
                        showControls = false
                        showLockHint()
                    }
                } else null,
                onSleepTimer = { showSleepDialog = true },
                onEnterPip = if (pipSupported && !isTv) {
                    { activity?.tryEnterPictureInPicture() }
                } else null
            )
        }

        // Skip intro / recap button, bottom right like the big streaming apps.
        val skip = skipTarget
        AnimatedVisibility(
            visible = skip != null && !isInPip && !isLocked && !showEpisodes,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.systemBars))
                .padding(end = 20.dp, bottom = if (controlsVisible) 120.dp else 28.dp)
        ) {
            val label = skip?.first ?: "Skip intro"
            Surface(
                onClick = { skip?.let { viewModel.seekTo(it.second) }; registerInteraction() },
                color = Color.White.copy(alpha = 0.92f),
                contentColor = Color.Black,
                shape = RoundedCornerShape(50),
                shadowElevation = 4.dp,
                modifier = Modifier.focusRequester(skipButtonFocus)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) {
                    Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }

        val nextEpisode = state.nextEpisode
        val creditsReached = state.creditsStartMs?.let { state.durationMs > 0L && position >= it } == true
        val showNextCard = !isInPip && !showEpisodes && nextEpisode != null && state.playbackError == null &&
            (state.isEnded || state.nextEpisodeCountdown != null || state.isLoadingNextEpisode ||
                (creditsReached && !state.creditsDismissed))
        if (showNextCard && nextEpisode != null) {
            NextEpisodeCard(
                next = nextEpisode,
                countdown = state.nextEpisodeCountdown,
                isLoading = state.isLoadingNextEpisode,
                showWatchCredits = !state.isEnded,
                onPlay = viewModel::playNextEpisode,
                onWatchCredits = viewModel::dismissCredits,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.systemBars))
                    .padding(end = 20.dp, bottom = if (controlsVisible) 120.dp else 28.dp)
            )
        }

        if (showEpisodes && !isInPip) {
            EpisodesPanel(
                state = state,
                onSelectSeason = viewModel::selectEpisodesSeason,
                onPlayEpisode = { episode ->
                    showEpisodes = false
                    if (episode.seasonNumber != state.seasonNumber || episode.episodeNumber != state.episodeNumber) {
                        viewModel.playEpisode(episode.seasonNumber, episode.episodeNumber)
                    }
                },
                onClose = { showEpisodes = false }
            )
        }
    }
}

@Composable
private fun PlayerPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color.Black.copy(alpha = 0.62f),
        contentColor = Color.White,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
        )
    }
}

@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerControlsOverlay(
    state: PlayerUiState,
    fitMode: FitMode,
    scrubPositionMs: Long?,
    onBack: () -> Unit,
    onTracks: () -> Unit,
    onSpeed: () -> Unit,
    onExternal: () -> Unit,
    onCycleFit: () -> Unit,
    onRotate: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubFinished: () -> Unit,
    onRetry: () -> Unit,
    previewFrame: SeekPreviewFrame? = null,
    onKeyScrub: (Long) -> Unit = {},
    onEpisodes: (() -> Unit)? = null,
    onNextEpisode: (() -> Unit)? = null,
    onEnterPip: (() -> Unit)? = null,
    playButtonFocus: FocusRequester? = null,
    seekBarFocus: FocusRequester? = null,
    showPhoneControls: Boolean = true,
    onLock: (() -> Unit)? = null,
    onSleepTimer: () -> Unit = {}
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize()) {
        // Soft scrims top and bottom instead of a flat grey veil, so the picture stays visible.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.72f),
                        0.22f to Color.Black.copy(alpha = 0.18f),
                        0.6f to Color.Black.copy(alpha = 0.18f),
                        1f to Color.Black.copy(alpha = 0.85f)
                    )
                )
        )
        Box(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.systemBars))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    state.subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 1
                        )
                    }
                }
                if (onLock != null) {
                    IconButton(onClick = onLock) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock screen", tint = Color.White)
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Video size: ${fitMode.label}") },
                            leadingIcon = { Icon(Icons.Default.AspectRatio, contentDescription = null) },
                            onClick = { menuOpen = false; onCycleFit() }
                        )
                        DropdownMenuItem(
                            text = { Text(sleepTimerMenuLabel(state)) },
                            leadingIcon = { Icon(Icons.Default.Bedtime, contentDescription = null) },
                            onClick = { menuOpen = false; onSleepTimer() }
                        )
                        if (onEnterPip != null) {
                            DropdownMenuItem(
                                text = { Text("Picture-in-picture") },
                                leadingIcon = { Icon(Icons.Default.PictureInPictureAlt, contentDescription = null) },
                                onClick = { menuOpen = false; onEnterPip() }
                            )
                        }
                        if (showPhoneControls) {
                            DropdownMenuItem(
                                text = { Text("Rotate screen") },
                                leadingIcon = { Icon(Icons.Default.ScreenRotation, contentDescription = null) },
                                onClick = { menuOpen = false; onRotate() }
                            )
                        }
                        if (state.availableExternalPlayers.any { it != ExternalPlayerTarget.INTERNAL_EXOPLAYER }) {
                            DropdownMenuItem(
                                text = { Text("Open in another player") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                                onClick = { menuOpen = false; onExternal() }
                            )
                        }
                    }
                }
            }

            val error = state.playbackError
            if (error != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(20.dp),
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
            } else if (scrubPositionMs == null) {
                // Center transport controls (hidden while skipping so the preview has room).
                // The overlay is laid out edge to edge (no inset padding) so this row is exactly screen-centred.
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TransportButton(Icons.Rounded.Replay10, "Back 10 seconds", 44.dp, onSeekBack)
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(84.dp)) {
                        if (state.isBuffering) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 3.dp,
                                strokeCap = StrokeCap.Round,
                                modifier = Modifier.size(56.dp)
                            )
                        } else {
                            TransportButton(
                                icon = when {
                                    state.isEnded -> Icons.Rounded.Replay
                                    state.isPlaying -> Icons.Rounded.Pause
                                    else -> Icons.Rounded.PlayArrow
                                },
                                description = if (state.isPlaying) "Pause" else "Play",
                                iconSize = 64.dp,
                                onClick = onTogglePlay,
                                focusRequester = playButtonFocus
                            )
                        }
                    }
                    TransportButton(Icons.Rounded.Forward10, "Forward 10 seconds", 44.dp, onSeekForward)
                }
            }

            // Bottom: seek bar, times and actions.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.systemBars))
                    .padding(start = 20.dp, end = 20.dp, bottom = 6.dp)
            ) {
                val duration = state.durationMs
                val shownPosition = scrubPositionMs ?: state.currentPositionMs
                val intro = state.skipSegments.intro?.let { seg -> seg.endMs?.let { seg.startMs..it } }
                ArgonSeekBar(
                    positionMs = state.currentPositionMs,
                    durationMs = duration,
                    bufferedMs = state.bufferedPositionMs,
                    scrubPositionMs = scrubPositionMs,
                    previewFrame = previewFrame,
                    introRange = intro,
                    creditsStartMs = state.creditsStartMs.takeIf { state.creditsFromData },
                    onScrub = onScrub,
                    onScrubFinished = onScrubFinished,
                    onKeyScrub = onKeyScrub,
                    focusRequester = seekBarFocus
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = buildString {
                            append(formatPlayerTime(shownPosition))
                            append("  /  ")
                            append(if (duration > 0L) formatPlayerTime(duration) else "--:--")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                    Spacer(Modifier.weight(1f))
                    val statusParts = listOfNotNull(
                        sleepTimerShortLabel(state),
                        if (state.playbackSpeed != 1.0f) "Speed ${formatSpeed(state.playbackSpeed)}" else null,
                        endsAtLabel(state)
                    )
                    if (statusParts.isNotEmpty()) {
                        Text(
                            text = statusParts.joinToString("  ·  "),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                ) {
                    if (onEpisodes != null) {
                        PlayerActionButton(Icons.AutoMirrored.Filled.List, "Episodes", onEpisodes)
                    }
                    PlayerActionButton(Icons.Default.ClosedCaption, "Audio & subtitles", onTracks)
                    PlayerActionButton(Icons.Default.Speed, "Speed", onSpeed)
                    Spacer(Modifier.weight(1f))
                    if (onNextEpisode != null) {
                        PlayerActionButton(Icons.Default.SkipNext, "Next episode", onNextEpisode)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    // Narrow (portrait phone) screens get icon-only buttons so the row never wraps.
    if (LocalConfiguration.current.screenWidthDp < 560) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = label, tint = Color.White) }
        return
    }
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** "Ends 22:41": when the video finishes at the current speed. */
private fun endsAtLabel(state: PlayerUiState): String? {
    if (state.durationMs <= 0L) return null
    val remaining = ((state.durationMs - state.currentPositionMs).coerceAtLeast(0L) / state.playbackSpeed).toLong()
    val endsAt = java.time.LocalTime.now().plusSeconds(remaining / 1000L)
    return "Ends " + endsAt.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
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

private fun sleepTimerShortLabel(state: PlayerUiState): String? = when {
    state.sleepAtEndOfEpisode -> "Sleep at end of episode"
    state.sleepTimerEndsAtMs != null -> {
        val minutes = ((state.sleepTimerEndsAtMs - System.currentTimeMillis()) / 60_000L + 1).coerceAtLeast(1)
        "Sleep in $minutes min"
    }
    else -> null
}

private fun sleepTimerMenuLabel(state: PlayerUiState): String = sleepTimerShortLabel(state) ?: "Sleep timer"

@Composable
private fun SleepTimerDialog(
    state: PlayerUiState,
    onDismiss: () -> Unit,
    onSelectMinutes: (Int?) -> Unit,
    onEndOfEpisode: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Bedtime, contentDescription = null) },
        title = { Text("Sleep timer") },
        text = {
            Column {
                val active = state.sleepTimerEndsAtMs != null || state.sleepAtEndOfEpisode
                TrackRow(label = "Off", selected = !active, onClick = { onSelectMinutes(null) })
                listOf(15, 30, 45, 60, 90).forEach { minutes ->
                    TrackRow(label = "$minutes minutes", selected = false, onClick = { onSelectMinutes(minutes) })
                }
                if (state.subtitle != null) {
                    TrackRow(label = "End of this episode", selected = state.sleepAtEndOfEpisode, onClick = onEndOfEpisode)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun NextEpisodeCard(
    next: NextEpisodeInfo,
    countdown: Int?,
    isLoading: Boolean,
    showWatchCredits: Boolean,
    onPlay: () -> Unit,
    onWatchCredits: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focus = remember { FocusRequester() }
    val isTv = LocalIsTv.current
    LaunchedEffect(isTv) { if (isTv) runCatching { focus.requestFocus() } }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        if (showWatchCredits && !isLoading) {
            Surface(
                onClick = onWatchCredits,
                color = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White,
                shape = RoundedCornerShape(50),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
            ) {
                Text("Watch credits", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
            }
        }
        // White pill that fills up as the countdown runs, like the big streaming apps.
        val progress = countdown?.let { 1f - it / 10f } ?: 0f
        Surface(
            onClick = onPlay,
            enabled = !isLoading,
            color = Color.White.copy(alpha = 0.92f),
            contentColor = Color.Black,
            shape = RoundedCornerShape(50),
            shadowElevation = 4.dp,
            modifier = Modifier.focusRequester(focus)
        ) {
            Box {
                if (countdown != null) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.Black)
                        Spacer(Modifier.width(8.dp))
                        Text("Loading…", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (countdown != null) "Next episode · ${countdown}s" else "Next episode",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    description: String,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(iconSize + 20.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

private const val LONG_PRESS_BOOST_MS = 450L

private fun formatSpeed(speed: Float): String =
    if (speed == 1.0f) "Normal" else String.format(Locale.US, "%sx", speed.toString().removeSuffix(".0"))

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
