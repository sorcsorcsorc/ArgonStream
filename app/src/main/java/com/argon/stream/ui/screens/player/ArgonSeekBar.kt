package com.argon.stream.ui.screens.player

import android.graphics.Bitmap
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Seek bar for the player. Thin while watching, it grows when touched, dragged or focused with a
 * TV remote. Shows what is buffered, highlights the intro, marks where the credits start, and while
 * skipping shows a preview bubble (frame + target time + how far you are jumping).
 *
 * TV: Left/Right move the target (bigger steps when the key is held), OK jumps right away;
 * otherwise the jump happens when the keys are released for a moment (see [onScrubFinished] caller).
 */
@Composable
internal fun ArgonSeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    scrubPositionMs: Long?,
    previewFrame: SeekPreviewFrame?,
    introRange: LongRange?,
    creditsStartMs: Long?,
    onScrub: (Long) -> Unit,
    onScrubFinished: () -> Unit,
    onKeyScrub: (deltaMs: Long) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var pressed by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val active = pressed || focused || scrubPositionMs != null
    val trackHeight by animateDpAsState(if (active) 8.dp else 4.dp, label = "track")
    val thumbSize by animateDpAsState(if (active) 20.dp else 12.dp, label = "thumb")
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.tertiary
    val enabled = durationMs > 0L

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val widthPx = constraints.maxWidth.toFloat()
            val density = LocalDensity.current

            // Preview bubble above the bar while skipping.
            if (scrubPositionMs != null && enabled) {
                val fraction = (scrubPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                val frame = previewFrame?.takeIf { abs(it.positionMs - scrubPositionMs) <= 30_000L }
                val bubbleWidth = if (frame != null) 176.dp else 112.dp
                val bubbleWidthPx = with(density) { bubbleWidth.toPx() }
                val x = (fraction * widthPx - bubbleWidthPx / 2f).coerceIn(0f, (widthPx - bubbleWidthPx).coerceAtLeast(0f))
                SeekPreviewBubble(
                    targetMs = scrubPositionMs,
                    deltaMs = scrubPositionMs - positionMs,
                    bitmap = frame?.bitmap,
                    modifier = Modifier
                        .offset { IntOffset(x.roundToInt(), 0) }
                        .width(bubbleWidth)
                        .padding(bottom = 10.dp)
                )
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .semantics { contentDescription = "Seek bar" }
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { event ->
                    if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val repeat = event.nativeKeyEvent.repeatCount
                    val step = when {
                        repeat >= 20 -> 60_000L
                        repeat >= 6 -> 30_000L
                        else -> 10_000L
                    }
                    when (event.key) {
                        Key.DirectionLeft -> { onKeyScrub(-step); true }
                        Key.DirectionRight -> { onKeyScrub(step); true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (scrubPositionMs != null) { onScrubFinished(); true } else false
                        }
                        else -> false
                    }
                }
                .focusable(enabled)
                .pointerInput(durationMs) {
                    if (durationMs <= 0L) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        pressed = true
                        fun positionFor(x: Float) = ((x / size.width).coerceIn(0f, 1f) * durationMs).toLong()
                        onScrub(positionFor(down.position.x))
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) { change.consume(); break }
                            onScrub(positionFor(change.position.x))
                            change.consume()
                        }
                        pressed = false
                        onScrubFinished()
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(thumbSize)) {
                val h = trackHeight.toPx()
                val top = (size.height - h) / 2f
                val radius = CornerRadius(h / 2f, h / 2f)
                fun xFor(ms: Long) = if (durationMs > 0L) (ms.toFloat() / durationMs).coerceIn(0f, 1f) * size.width else 0f

                drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(0f, top), Size(size.width, h), radius)
                drawRoundRect(Color.White.copy(alpha = 0.38f), Offset(0f, top), Size(xFor(bufferedMs), h), radius)
                introRange?.let { range ->
                    val start = xFor(range.first)
                    drawRect(accent.copy(alpha = 0.55f), Offset(start, top), Size((xFor(range.last) - start).coerceAtLeast(2f), h))
                }
                val playedX = xFor(scrubPositionMs ?: positionMs)
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(primary.copy(alpha = 0.75f), primary), endX = playedX.coerceAtLeast(1f)),
                    topLeft = Offset(0f, top),
                    size = Size(playedX, h),
                    cornerRadius = radius
                )
                creditsStartMs?.takeIf { it in 1 until durationMs }?.let { credits ->
                    val cx = xFor(credits)
                    drawRect(Color.White.copy(alpha = 0.9f), Offset(cx - 1.5f, top - 2f), Size(3f, h + 4f))
                }
                if (enabled) {
                    val r = thumbSize.toPx() / 2f
                    if (active) drawCircle(primary.copy(alpha = 0.28f), radius = r * 1.6f, center = Offset(playedX, size.height / 2f))
                    drawCircle(Color.White, radius = r, center = Offset(playedX, size.height / 2f))
                    drawCircle(primary, radius = r * 0.45f, center = Offset(playedX, size.height / 2f))
                }
            }
        }
    }
}

@Composable
private fun SeekPreviewBubble(targetMs: Long, deltaMs: Long, bitmap: Bitmap?, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        if (bitmap != null) {
            val image = remember(bitmap) { bitmap.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(99.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .background(Color.Black)
            )
            Box(modifier = Modifier.size(6.dp))
        }
        Surface(color = Color.Black.copy(alpha = 0.78f), contentColor = Color.White, shape = RoundedCornerShape(50)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text(formatPlayerTime(targetMs), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = formatSeekDelta(deltaMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (deltaMs >= 0) MaterialTheme.colorScheme.primary else Color(0xFFFFB4A9)
                )
            }
        }
    }
}

internal fun formatPlayerTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.US, "%d:%02d", minutes, seconds)
}

/** "+1:30" / "−0:20" — how far a skip moves from the current position. */
internal fun formatSeekDelta(deltaMs: Long): String {
    val sign = if (deltaMs >= 0) "+" else "−"
    val seconds = abs(deltaMs) / 1000L
    return if (seconds < 60) "$sign${seconds}s" else "$sign${formatPlayerTime(abs(deltaMs))}"
}
