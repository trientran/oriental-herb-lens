package com.uri.lee.dl.core.firebase

import com.uri.lee.dl.core.common.AppInfo
import dev.gitlive.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Remote Config values. Unset keys read as "", 0 or false, except those in [DEFAULTS].
 * Content sync keys deliberately have no default: "" means "nothing published, use the bundled copy".
 */
class RemoteConfigClient internal constructor(
    private val remoteConfig: FirebaseRemoteConfig,
    private val app: AppInfo,
) {
    private val setupLock = Mutex()
    private var isSetUp = false

    /** Fetches newer values, if the last fetch is old enough, and makes them current. */
    suspend fun fetchAndActivate() {
        setUp()
        remoteConfig.fetchAndActivate()
    }

    fun string(key: String): String = remoteConfig.getValue(key).asString()
    fun long(key: String): Long = remoteConfig.getValue(key).asLong()
    fun boolean(key: String): Boolean = remoteConfig.getValue(key).asBoolean()

    private suspend fun setUp() = setupLock.withLock {
        if (isSetUp) return@withLock
        // Debug builds see newly published values immediately
        remoteConfig.settings { minimumFetchInterval = if (app.isDebug) Duration.ZERO else 1.hours }
        remoteConfig.setDefaults(*DEFAULTS.toTypedArray())
        isSetUp = true
    }

    companion object {
        private val DEFAULTS = listOf(
            // Version codes; 0 means no update prompt
            "min_supported_version_android" to 0L,
            "recommended_version_android" to 0L,
            "min_supported_version_ios" to 0L,
            "recommended_version_ios" to 0L,
            // true shows the "service unavailable" notice and closes the app
            "service_suspended" to false,
        )
    }
}
