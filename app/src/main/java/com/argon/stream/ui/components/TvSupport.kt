package com.argon.stream.ui.components

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** True when running on Android TV / Google TV / Fire TV (remote control, no touch screen). */
fun Context.isTelevision(): Boolean {
    val uiMode = (getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType
    return uiMode == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        packageManager.hasSystemFeature("amazon.hardware.fire_tv")
}

/** Provided once in MainActivity; screens read it to switch to remote-friendly behaviour. */
val LocalIsTv = staticCompositionLocalOf { false }

/**
 * Clear outline (and a slight zoom) on the element the remote / keyboard has focused.
 * Place it before `clickable` so it sees the clickable's focus.
 *
 * The zoom and outline are animated and read only in the draw phase, so moving focus never
 * re-composes or re-measures the card. That keeps D-pad scrolling smooth on slower TV boxes.
 */
fun Modifier.focusHighlight(shape: Shape = RoundedCornerShape(12.dp), zoom: Boolean = false): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.primary
    val progress by animateFloatAsState(if (focused) 1f else 0f, animationSpec = tween(140), label = "focus")
    this
        .onFocusChanged { focused = it.hasFocus }
        .graphicsLayer {
            if (zoom) {
                val s = 1f + 0.05f * progress
                scaleX = s
                scaleY = s
            }
        }
        .drawWithContent {
            drawContent()
            if (progress > 0f) {
                val outline = shape.createOutline(size, layoutDirection, this)
                drawOutline(outline, color = color.copy(alpha = progress), style = Stroke(width = 3.dp.toPx()))
            }
        }
}
