package com.argon.stream.core.player

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import com.argon.stream.domain.model.ExternalPlayerTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExternalPlayerLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getInstalledExternalPlayers(): List<ExternalPlayerTarget> {
        val pm = context.packageManager
        return ExternalPlayerTarget.entries.filter { target ->
            when (target) {
                ExternalPlayerTarget.INTERNAL_EXOPLAYER -> true
                ExternalPlayerTarget.SYSTEM_CHOOSER -> true
                else -> target.packageName?.let { isPackageInstalled(pm, it) } == true
            }
        }
    }

    /**
     * Launches an installed external player (VLC, Just Player, MX Player, or System Chooser)
     * with title, resume position, and HTTP headers where supported.
     * Returns true if the Activity was launched, or false so the caller can fall back to internal Media3.
     */
    fun launchExternalPlayer(
        target: ExternalPlayerTarget,
        streamUrl: String,
        title: String,
        resumePositionMs: Long = 0L,
        headers: Map<String, String> = emptyMap()
    ): Boolean {
        if (target == ExternalPlayerTarget.INTERNAL_EXOPLAYER || streamUrl.isBlank()) return false

        return runCatching {
            val uri = Uri.parse(streamUrl)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndTypeAndNormalize(uri, "video/*")
                putExtra("title", title)
                putExtra("name", title)
                if (resumePositionMs > 0L) {
                    putExtra("position", resumePositionMs.toInt()) // MX / Just Player convention (ms)
                    putExtra("from_start", false)
                }
                if (headers.isNotEmpty()) {
                    val headerArray = headers.flatMap { listOf(it.key, it.value) }.toTypedArray()
                    putExtra("headers", headerArray) // MX / Just Player convention
                    val bundle = Bundle().apply {
                        headers.forEach { (k, v) -> putString(k, v) }
                    }
                    putExtra("android.media.intent.extra.HTTP_HEADERS", bundle)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (!target.packageName.isNullOrBlank()) {
                intent.setPackage(target.packageName)
            }

            val finalIntent = if (target == ExternalPlayerTarget.SYSTEM_CHOOSER) {
                Intent.createChooser(intent, "Play stream with...").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                intent
            }

            context.startActivity(finalIntent)
            true
        }.getOrDefault(false)
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return runCatching {
            pm.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }
}