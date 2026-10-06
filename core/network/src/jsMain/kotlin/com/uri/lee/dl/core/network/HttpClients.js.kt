package com.uri.lee.dl.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import kotlinx.io.IOException

internal actual fun platformEngine(): HttpClientEngine = Js.create()

internal actual fun Throwable.asNetworkException(): Throwable =
    if (this is Exception) this else IOException(message ?: "Network request failed", this)
