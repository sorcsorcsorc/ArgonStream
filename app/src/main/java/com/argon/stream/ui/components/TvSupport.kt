package com.argon.stream.ui.components

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
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
 */
fun Modifier.focusHighlight(shape: Shape = RoundedCornerShape(12.dp), zoom: Boolean = false): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.primary
    this
        .onFocusChanged { focused = it.hasFocus }
        .then(if (focused && zoom) Modifier.scale(1.05f) else Modifier)
        .then(if (focused) Modifier.border(3.dp, color, shape) else Modifier)
}
