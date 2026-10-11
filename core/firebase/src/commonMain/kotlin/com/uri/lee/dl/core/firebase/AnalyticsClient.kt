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
     * In the browser Analytics sets cookies as soon as it starts, so it starts only once usage
     * statistics are known to be on; on Android and iOS it starts with the app and is switched off if needed.
     */
    private val startOnlyWhenEnabled: Boolean,
    /**
     * In the browser a Kotlin Long is an object, which reached Google Analytics as
     * {"low_1": …, "high_1": 0} rather than a number; there it's sent as a JavaScript number
     * (exact for whole numbers up to 2^53, which species keys and counts are far below).
     */
    private val longsAsNumbers: Boolean = startOnlyWhenEnabled,
) {
    private var analytics: FirebaseAnalytics? = null

    private fun analytics(): FirebaseAnalytics? =
        analytics ?: if (startOnlyWhenEnabled && !enabled) null else provider().also { analytics = it }

    private var enabled = false

    /** Whether the user's setting has been applied yet; until then, in the browser, events wait in [pending]. */
    private var decided = false
    private val pending = mutableListOf<Pair<String, Map<String, Any>>>()

    fun logEvent(name: String, parameters: Map<String, Any>) {
        val values = if (longsAsNumbers) parameters.mapValues { (_, v) -> if (v is Long) v.toDouble() else v } else parameters
        // Logged at launch, before the setting is read (the browser waits for it to start Analytics)
        if (startOnlyWhenEnabled && !decided) {
            if (pending.size < MAX_PENDING) pending += name to values
            return
        }
        analytics()?.logEvent(name, values)
    }

    /** Applies the user's setting; events logged before it was known are sent, or dropped if it's off. */
    fun setCollectionEnabled(enabled: Boolean) {
        this.enabled = enabled
        decided = true
        analytics()?.setAnalyticsCollectionEnabled(enabled)
        if (enabled) pending.forEach { (name, values) -> analytics()?.logEvent(name, values) }
        pending.clear()
    }

    fun setUserProperty(name: String, value: String) {
        analytics()?.setUserProperty(name, value)
    }

    private companion object {
        const val MAX_PENDING = 50
    }
}
