package com.uri.lee.dl.core.common

/** Current time, injectable so tests can control ordering. */
fun interface Clock {
    fun nowMillis(): Long

    companion object {
        val System = Clock { java.lang.System.currentTimeMillis() }
    }
}
