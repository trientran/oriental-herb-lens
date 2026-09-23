package com.uri.lee.dl.data.content

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.uri.lee.dl.domain.model.ContentKind
import com.uri.lee.dl.domain.model.ContentRelease
import kotlinx.coroutines.tasks.await

/** Where published releases are announced. */
fun interface ReleaseSource {
    suspend fun latest(): Map<ContentKind, ContentRelease>
}

/**
 * Remote Config keys: `herb_model_url`, `herb_model_sha256`, `herb_catalog_url`,
 * `herb_catalog_sha256`. A kind is published only when both its URL and checksum are set.
 */
class RemoteConfigReleaseSource(private val remoteConfig: FirebaseRemoteConfig) : ReleaseSource {

    override suspend fun latest(): Map<ContentKind, ContentRelease> {
        remoteConfig.fetchAndActivate().await()
        return ContentKind.entries.mapNotNull { kind ->
            val url = remoteConfig.getString(urlKey(kind)).trim()
            val sha256 = remoteConfig.getString(sha256Key(kind)).trim()
            if (url.isEmpty() || sha256.isEmpty()) null else kind to ContentRelease(url, sha256)
        }.toMap()
    }

    companion object {
        fun urlKey(kind: ContentKind) = "${prefix(kind)}_url"
        fun sha256Key(kind: ContentKind) = "${prefix(kind)}_sha256"
        private fun prefix(kind: ContentKind) = when (kind) {
            ContentKind.MODEL -> "herb_model"
            ContentKind.CATALOG -> "herb_catalog"
        }
    }
}
