package com.argon.stream.core.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import kotlin.math.roundToInt

/**
 * Shared between the player screen (which knows whether a video is playing) and MainActivity
 * (which receives onUserLeaveHint on Android 8–11, where auto-enter is not available).
 */
object PictureInPictureState {
    @Volatile
    var autoEnterEnabled: Boolean = false

    @Volatile
    var aspectRatio: Rational = Rational(16, 9)
}

fun Context.supportsPictureInPicture(): Boolean =
    packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

/** Android only accepts aspect ratios between 1:2.39 and 2.39:1. */
fun pipAspectRatio(videoAspect: Float): Rational {
    if (videoAspect <= 0f || videoAspect.isNaN()) return Rational(16, 9)
    val clamped = videoAspect.coerceIn(1f / 2.38f, 2.38f)
    return Rational((clamped * 1000).roundToInt(), 1000)
}

fun buildPipParams(autoEnter: Boolean): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder()
        .setAspectRatio(PictureInPictureState.aspectRatio)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        builder.setAutoEnterEnabled(autoEnter)
        builder.setSeamlessResizeEnabled(true)
    }
    return builder.build()
}

/** Returns true when the window entered picture-in-picture. */
fun Activity.tryEnterPictureInPicture(): Boolean =
    supportsPictureInPicture() && runCatching { enterPictureInPictureMode(buildPipParams(autoEnter = true)) }.getOrDefault(false)
