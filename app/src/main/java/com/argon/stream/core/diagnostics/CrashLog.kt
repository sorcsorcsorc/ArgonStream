package com.argon.stream.core.diagnostics

import android.content.Context
import android.os.Build
import com.argon.stream.BuildConfig
import com.argon.stream.core.network.RedactingLoggingInterceptor
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the most recent crashes in a private file so they can be shared from Settings.
 * Nothing leaves the device unless the user taps Share. URLs with keys/tokens are redacted.
 */
object CrashLog {
    private const val FILE_NAME = "crash-log.txt"
    private const val MAX_CHARS = 24_000

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { record(appContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String? =
        file(context).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        file(context).delete()
    }

    private fun record(context: Context, thread: Thread, throwable: Throwable) {
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = buildString {
            appendLine("=== Crash $time ===")
            appendLine("App ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name}")
            appendLine(RedactingLoggingInterceptor.sanitizeUrl(stack))
        }
        val existing = read(context).orEmpty()
        file(context).writeText((entry + "\n" + existing).take(MAX_CHARS))
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)
}
