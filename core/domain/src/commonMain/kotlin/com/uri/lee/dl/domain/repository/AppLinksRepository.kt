package com.uri.lee.dl.domain.repository

import com.uri.lee.dl.domain.model.AppLinks

/** The contact and dataset links; published remotely, so they change without a release. */
interface AppLinksRepository {
    /** The published links, or the built-in ones for any that aren't published or aren't valid. */
    suspend fun links(): AppLinks
}
