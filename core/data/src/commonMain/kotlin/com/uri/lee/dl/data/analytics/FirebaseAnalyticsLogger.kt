package com.uri.lee.dl.data.analytics

import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.common.AppInfo
import com.uri.lee.dl.core.firebase.AnalyticsClient
import com.uri.lee.dl.domain.analytics.Analytics
import com.uri.lee.dl.domain.analytics.AnalyticsEvent

/**
 * Sends events to Google Analytics for Firebase. Whether anything is sent follows the user's
 * setting, applied by [UsageStatisticsSync]. Failures never reach the app.
 */
internal class FirebaseAnalyticsLogger(private val analytics: AnalyticsClient) : Analytics {

    override fun log(event: AnalyticsEvent) = safely {
        log.d { "${event.name} ${event.parameters}" }
        analytics.logEvent(event.name, event.parameters)
    }

    override fun screen(name: String) = safely {
        log.d { "screen $name" }
        analytics.logEvent(SCREEN_VIEW, mapOf(SCREEN_NAME to name, SCREEN_CLASS to name))
    }

    private fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            log.w(e) { "Analytics event not sent" }
        }
    }

    private companion object {
        // GA4's built-in screen view event
        const val SCREEN_VIEW = "screen_view"
        const val SCREEN_NAME = "screen_name"
        const val SCREEN_CLASS = "screen_class"
        val log = Logger.withTag("Analytics")
    }
}

/** Turns collection on or off with the user's setting, and labels debug builds so they can be filtered out. */
internal class UsageStatisticsSync(
    private val analytics: AnalyticsClient,
    private val app: AppInfo,
) {
    fun apply(enabled: Boolean) {
        try {
            analytics.setCollectionEnabled(enabled)
            analytics.setUserProperty("build_type", if (app.isDebug) "debug" else "release")
        } catch (e: Exception) {
            Logger.withTag("Analytics").w(e) { "Collection setting not applied" }
        }
    }
}
