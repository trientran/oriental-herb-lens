package com.uri.lee.dl.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Injected instead of referencing [Dispatchers] directly, so tests can run on a test dispatcher. */
data class AppDispatchers(
    val io: CoroutineDispatcher = ioDispatcher,
    val default: CoroutineDispatcher = Dispatchers.Default,
    val main: CoroutineDispatcher = Dispatchers.Main,
)

/** `Dispatchers.IO` where the platform has one; the browser has a single thread and no blocking IO. */
internal expect val ioDispatcher: CoroutineDispatcher
