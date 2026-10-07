package com.uri.lee.dl.data.upload

import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

/**
 * Sends [request] and, while the upload Worker answers 429 (more than its per-minute limit, e.g.
 * someone sharing hundreds of photos in a row), waits as long as it asks and sends it again, so a
 * long upload slows down instead of failing.
 */
internal suspend fun sendWithinRateLimit(attempts: Int = 5, request: suspend () -> HttpResponse): HttpResponse {
    var response = request()
    repeat(attempts - 1) {
        if (response.status != HttpStatusCode.TooManyRequests) return response
        val wait = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()?.coerceIn(1, 120) ?: 60
        delay(wait.seconds)
        response = request()
    }
    return response
}
