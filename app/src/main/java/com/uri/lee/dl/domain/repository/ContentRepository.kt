package com.uri.lee.dl.domain.repository

import com.uri.lee.dl.domain.model.ContentKind
import com.uri.lee.dl.domain.model.ContentRelease
import com.uri.lee.dl.domain.model.InstallResult

interface ContentRepository {
    /** The releases currently published (Remote Config); kinds with nothing published are absent. */
    suspend fun publishedReleases(): Map<ContentKind, ContentRelease>

    /** The release installed on this device, or null while the bundled copy is in use. */
    suspend fun installedRelease(kind: ContentKind): ContentRelease?

    /** Downloads, verifies and activates [release]. Never touches the working copy unless it succeeds. */
    suspend fun install(kind: ContentKind, release: ContentRelease): InstallResult
}
