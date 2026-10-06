package com.uri.lee.dl.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

internal actual fun platformEngine(): HttpClientEngine = Js.create()
