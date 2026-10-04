package com.uri.lee.dl.core.common

import kotlin.time.ExperimentalTime

/** Current time, injectable so tests can control ordering. */
fun interface Clock {
    fun nowMillis(): Long

    companion object {
        @OptIn(ExperimentalTime::class)
        val System = Clock { kotlin.time.Clock.System.now().toEpochMilliseconds() }
    }
}
