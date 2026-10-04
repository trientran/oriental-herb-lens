package com.uri.lee.dl.data.content

import com.uri.lee.dl.core.firebase.RemoteConfigClient
import com.uri.lee.dl.domain.model.ContentKind
import com.uri.lee.dl.domain.model.ContentRelease

/** Where published releases are announced. */
internal fun interface ReleaseSource {
    suspend fun latest(): Map<ContentKind, ContentRelease>
}

/**
 * Remote Config keys: `herb_model_url`, `herb_model_sha256`, `herb_catalog_url`,
 * `herb_catalog_sha256`. A kind is published only when both its URL and checksum are set.
 */
internal class RemoteConfigReleaseSource(private val remoteConfig: RemoteConfigClient) : ReleaseSource {

    override suspend fun latest(): Map<ContentKind, ContentRelease> {
        remoteConfig.fetchAndActivate()
        return ContentKind.entries.mapNotNull { kind ->
            val url = remoteConfig.string(urlKey(kind)).trim()
            val sha256 = remoteConfig.string(sha256Key(kind)).trim()
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
