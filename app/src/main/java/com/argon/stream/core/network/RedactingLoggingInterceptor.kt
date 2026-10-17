package com.argon.stream.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

data class DiagnosticLogEntry(
    val timestampMs: Long,
    val method: String,
    val sanitizedUrl: String,
    val statusCode: Int?,
    val durationMs: Long,
    val errorSummary: String? = null
)

/**
 * Captures sanitized network diagnostics while strictly redacting API keys,
 * Bearer tokens, TorBox requestdl tokens, and sensitive query parameters.
 */
@Singleton
class RedactingLoggingInterceptor @Inject constructor() : Interceptor {

    private val entries = CopyOnWriteArrayList<DiagnosticLogEntry>()

    fun getRecentLogs(): List<DiagnosticLogEntry> = entries.toList().asReversed()

    fun clearLogs() = entries.clear()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startNs = System.nanoTime()
        val sanitizedUrl = sanitizeUrl(request.url.toString())

        return try {
            val response = chain.proceed(request)
            val durationMs = (System.nanoTime() - startNs) / 1_000_000
            record(
                DiagnosticLogEntry(
                    timestampMs = System.currentTimeMillis(),
                    method = request.method,
                    sanitizedUrl = sanitizedUrl,
                    statusCode = response.code,
                    durationMs = durationMs
                )
            )
            response
        } catch (e: Exception) {
            val durationMs = (System.nanoTime() - startNs) / 1_000_000
            record(
                DiagnosticLogEntry(
                    timestampMs = System.currentTimeMillis(),
                    method = request.method,
                    sanitizedUrl = sanitizedUrl,
                    statusCode = null,
                    durationMs = durationMs,
                    errorSummary = e.javaClass.simpleName
                )
            )
            throw e
        }
    }

    private fun record(entry: DiagnosticLogEntry) {
        entries.add(entry)
        while (entries.size > MAX_LOG_ENTRIES) {
            entries.removeAt(0)
        }
    }

    companion object {
        private const val MAX_LOG_ENTRIES = 150
        private val SENSITIVE_QUERY_REGEX =
            Regex("(?i)(api_key|token|apikey|auth|password|secret|key)=([^&]+)")

        fun sanitizeUrl(rawUrl: String): String {
            return rawUrl.replace(SENSITIVE_QUERY_REGEX) { matchResult ->
                "${matchResult.groupValues[1]}=[REDACTED]"
            }
        }
    }
}