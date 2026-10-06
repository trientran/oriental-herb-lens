package com.uri.lee.dl.core.network

import com.uri.lee.dl.core.common.AppInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The app's one HTTP client. It identifies itself as Herb Lens: Cloudflare rejects some generic
 * client user agents with 403, and GBIF asks API users to identify their app.
 */
fun herbLensHttpClient(engine: HttpClientEngine, app: AppInfo): HttpClient = HttpClient(engine) {
    // Browsers send their own user agent, and setting one would make every cross-origin request
    // (GBIF, R2) need a CORS preflight
    if (app.platform != "web") {
        install(UserAgent) { agent = "HerbLens/${app.versionName} (${userAgentPlatform(app.platform)})" }
    }
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 60_000
    }
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
}.apply {
    // In the browser a failed fetch (offline, blocked by an extension, CORS) arrives as a JS
    // TypeError, which isn't an Exception: callers' `catch (e: Exception)` would miss it
    plugin(HttpSend).intercept { request ->
        try {
            execute(request)
        } catch (e: Throwable) {
            if (e is Exception || e is Error) throw e
            throw IOException(e.message ?: "Network request failed", e)
        }
    }
}

private fun userAgentPlatform(platform: String) = when (platform) {
    "ios" -> "iOS"
    "android" -> "Android"
    else -> platform
}

/** Provides the [HttpClient]; needs an [AppInfo] in the graph. */
val networkModule: Module = module {
    single { herbLensHttpClient(platformEngine(), get()) }
}

internal expect fun platformEngine(): HttpClientEngine
