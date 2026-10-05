package com.uri.lee.dl.core.firebase

import dev.gitlive.firebase.analytics.FirebaseAnalytics

/**
 * Google Analytics for Firebase, set up for research use without tracking: no advertising ID or
 * ad personalisation (see the Android manifest and the iOS Info.plist and Podfile), and no user ID,
 * so events can't be tied to an account.
 */
class AnalyticsClient internal constructor(private val analytics: FirebaseAnalytics) {

    fun logEvent(name: String, parameters: Map<String, Any>) = analytics.logEvent(name, parameters)

    fun setCollectionEnabled(enabled: Boolean) = analytics.setAnalyticsCollectionEnabled(enabled)

    fun setUserProperty(name: String, value: String) = analytics.setUserProperty(name, value)
}
