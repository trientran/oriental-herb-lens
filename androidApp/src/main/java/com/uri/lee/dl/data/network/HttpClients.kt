package com.uri.lee.dl.data.network

import com.uri.lee.dl.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The app's one HTTP client. It identifies itself as Herb Lens: Cloudflare rejects some generic
 * client user agents with 403, and GBIF asks API users to identify their app.
 */
fun herbLensHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    install(UserAgent) { agent = "HerbLens/${BuildConfig.VERSION_NAME} (Android)" }
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 60_000
    }
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
}
