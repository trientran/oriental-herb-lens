package com.uri.lee.dl.data.firebase

import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.firebase.RemoteConfigClient
import com.uri.lee.dl.domain.model.AppStatus
import com.uri.lee.dl.domain.model.UpdatePolicy
import com.uri.lee.dl.domain.repository.AppStatusRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * App-wide switches from Remote Config: update prompts (`min_supported_version_<platform>`,
 * `recommended_version_<platform>`, compared with this build's version code) and `service_suspended`.
 * Bans are enforced by Firestore security rules, not checked here.
 */
internal class DefaultAppStatusRepository(
    private val remoteConfig: RemoteConfigClient,
    private val versionCode: Long,
    private val platform: String,
) : AppStatusRepository {

    override fun observe(): Flow<AppStatus> = flow {
        try {
            remoteConfig.fetchAndActivate()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "Remote Config fetch failed; using cached values" }
        }
        emit(
            AppStatus(
                update = updatePolicy(
                    current = versionCode,
                    minimum = remoteConfig.long("min_supported_version_$platform"),
                    recommended = remoteConfig.long("recommended_version_$platform"),
                ),
                isSuspended = remoteConfig.boolean(SERVICE_SUSPENDED),
            )
        )
    }

    companion object {
        const val SERVICE_SUSPENDED = "service_suspended"
        private val log = Logger.withTag("AppStatus")

        /** A version of 0 (the default) means "not set". */
        fun updatePolicy(current: Long, minimum: Long, recommended: Long): UpdatePolicy = when {
            minimum > 0 && current < minimum -> UpdatePolicy.REQUIRED
            recommended > 0 && current < recommended -> UpdatePolicy.RECOMMENDED
            else -> UpdatePolicy.NONE
        }
    }
}
