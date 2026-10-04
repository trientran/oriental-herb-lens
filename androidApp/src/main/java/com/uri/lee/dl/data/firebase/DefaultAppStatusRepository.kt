package com.uri.lee.dl.data.firebase

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.uri.lee.dl.domain.model.AppStatus
import com.uri.lee.dl.domain.model.UpdatePolicy
import com.uri.lee.dl.domain.repository.AppStatusRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * App-wide switches from Remote Config: update prompts (`min_supported_version_android`,
 * `recommended_version_android`, compared with this build's version code) and `service_suspended`.
 * Bans are enforced by Firestore security rules, not checked here.
 */
class DefaultAppStatusRepository(
    private val remoteConfig: FirebaseRemoteConfig,
    private val versionCode: Long,
) : AppStatusRepository {

    override fun observe(): Flow<AppStatus> = flow {
        try {
            remoteConfig.fetchAndActivate().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Remote Config fetch failed; using cached values")
        }
        emit(
            AppStatus(
                update = updatePolicy(
                    current = versionCode,
                    minimum = remoteConfig.getLong(MIN_SUPPORTED_VERSION),
                    recommended = remoteConfig.getLong(RECOMMENDED_VERSION),
                ),
                isSuspended = remoteConfig.getBoolean(SERVICE_SUSPENDED),
            )
        )
    }

    companion object {
        const val MIN_SUPPORTED_VERSION = "min_supported_version_android"
        const val RECOMMENDED_VERSION = "recommended_version_android"
        const val SERVICE_SUSPENDED = "service_suspended"

        /** A version of 0 (the default) means "not set". */
        fun updatePolicy(current: Long, minimum: Long, recommended: Long): UpdatePolicy = when {
            minimum > 0 && current < minimum -> UpdatePolicy.REQUIRED
            recommended > 0 && current < recommended -> UpdatePolicy.RECOMMENDED
            else -> UpdatePolicy.NONE
        }
    }
}
