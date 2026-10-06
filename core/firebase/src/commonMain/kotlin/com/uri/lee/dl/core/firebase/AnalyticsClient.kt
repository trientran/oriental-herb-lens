package com.uri.lee.dl.core.firebase

import dev.gitlive.firebase.analytics.FirebaseAnalytics

/**
 * Google Analytics for Firebase, set up for research use without tracking: no advertising ID or
 * ad personalisation (see the Android manifest and the iOS Info.plist and Podfile), and no user ID,
 * so events can't be tied to an account.
 */
class AnalyticsClient internal constructor(
    private val provider: () -> FirebaseAnalytics,
    /**
     * In the browser Analytics sets cookies as soon as it starts, so it isn't started until the
     * visitor allows it; on Android and iOS it starts with the app and is switched off if needed.
     */
    private val startOnlyWhenEnabled: Boolean,
) {
    private var analytics: FirebaseAnalytics? = null

    private fun analytics(): FirebaseAnalytics? =
        analytics ?: if (startOnlyWhenEnabled && !enabled) null else provider().also { analytics = it }

    private var enabled = false

    fun logEvent(name: String, parameters: Map<String, Any>) {
        analytics()?.logEvent(name, parameters)
    }

    fun setCollectionEnabled(enabled: Boolean) {
        this.enabled = enabled
        analytics()?.setAnalyticsCollectionEnabled(enabled)
    }

    fun setUserProperty(name: String, value: String) {
        analytics()?.setUserProperty(name, value)
    }
}
