package com.uri.lee.dl.feature.training

import kotlin.math.roundToLong

/** A time to show: whole milliseconds under a second, whole seconds under a minute, else minutes and seconds (no decimals, so no locale's separator is needed). */
sealed interface ShownDuration {
    data class Millis(val millis: Long) : ShownDuration
    data class Seconds(val seconds: Long) : ShownDuration
    data class Minutes(val minutes: Long, val seconds: Long) : ShownDuration

    companion object {
        fun of(millis: Long): ShownDuration {
            if (millis < 1_000) return Millis(millis.coerceAtLeast(0))
            val seconds = (millis / 1_000.0).roundToLong()
            return if (seconds < 60) Seconds(seconds) else Minutes(seconds / 60, seconds % 60)
        }
    }
}
