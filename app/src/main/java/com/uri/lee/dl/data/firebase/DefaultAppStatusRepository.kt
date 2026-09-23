package com.uri.lee.dl.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.google.firebase.firestore.toObject
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.uri.lee.dl.FireStoreMobile
import com.uri.lee.dl.domain.model.AppStatus
import com.uri.lee.dl.domain.model.UpdatePolicy
import com.uri.lee.dl.domain.repository.AppStatusRepository
import com.uri.lee.dl.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Update prompts come from Remote Config (`min_supported_version_android`,
 * `recommended_version_android`, compared with this build's version code).
 *
 * Bans and the suspension switch still come from Firestore `config/mobile`, which older app
 * versions also read. Phase 2 enforces bans in security rules and drops this read.
 */
class DefaultAppStatusRepository(
    private val remoteConfig: FirebaseRemoteConfig,
    private val db: FirebaseFirestore,
    private val auth: AuthRepository,
    private val versionCode: Long,
) : AppStatusRepository {

    override fun observe(): Flow<AppStatus> {
        val update = flow {
            try {
                remoteConfig.fetchAndActivate().await()
            } catch (e: Exception) {
                Timber.w(e, "Remote Config fetch failed; using cached values")
            }
            emit(
                updatePolicy(
                    current = versionCode,
                    minimum = remoteConfig.getLong(MIN_SUPPORTED_VERSION),
                    recommended = remoteConfig.getLong(RECOMMENDED_VERSION),
                )
            )
        }
        val mobileConfig = db.collection(FirestorePaths.CONFIG).document(FirestorePaths.MOBILE_CONFIG_DOC)
            .snapshots()
            .map { it.toObject<FireStoreMobile>() ?: FireStoreMobile() }
        return combine(update, mobileConfig, auth.observeUserId()) { policy, mobile, uid ->
            AppStatus(
                update = policy,
                isCurrentUserBanned = uid != null && uid in mobile.bannedUsers,
                isSuspended = mobile.stackOverflow,
            )
        }.distinctUntilChanged()
    }

    companion object {
        const val MIN_SUPPORTED_VERSION = "min_supported_version_android"
        const val RECOMMENDED_VERSION = "recommended_version_android"

        /** A version of 0 (the default) means "not set". */
        fun updatePolicy(current: Long, minimum: Long, recommended: Long): UpdatePolicy = when {
            minimum > 0 && current < minimum -> UpdatePolicy.REQUIRED
            recommended > 0 && current < recommended -> UpdatePolicy.RECOMMENDED
            else -> UpdatePolicy.NONE
        }
    }
}
