package com.argon.stream.core.network

import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>
    data class Error(
        val code: Int? = null,
        val userMessage: String,
        val diagnosticReason: String,
        val isRetryable: Boolean = true
    ) : NetworkResult<Nothing>
}

suspend inline fun <T> safeApiCall(
    crossinline block: suspend () -> T
): NetworkResult<T> {
    return try {
        NetworkResult.Success(block())
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: SocketTimeoutException) {
        NetworkResult.Error(
            userMessage = "Connection timed out. Check your network or increase timeout in Settings.",
            diagnosticReason = "SocketTimeoutException",
            isRetryable = true
        )
    } catch (e: HttpException) {
        val code = e.code()
        val message = when (code) {
            401, 403 -> "Authentication failed or expired API key (HTTP $code)."
            404 -> "Requested resource or endpoint was not found (HTTP 404)."
            429 -> "Rate limit reached (HTTP 429). Please wait before retrying."
            in 500..599 -> "Remote provider server error (HTTP $code)."
            else -> "HTTP error $code occurred."
        }
        NetworkResult.Error(
            code = code,
            userMessage = message,
            diagnosticReason = "HTTP_$code",
            isRetryable = code == 429 || code in 500..599
        )
    } catch (e: IOException) {
        NetworkResult.Error(
            userMessage = "Network unavailable. Verify Wi-Fi or mobile data connection.",
            diagnosticReason = "IOException: ${e.javaClass.simpleName}",
            isRetryable = true
        )
    } catch (e: Exception) {
        NetworkResult.Error(
            userMessage = "Unexpected response format or internal error.",
            diagnosticReason = e.javaClass.simpleName,
            isRetryable = false
        )
    }
}