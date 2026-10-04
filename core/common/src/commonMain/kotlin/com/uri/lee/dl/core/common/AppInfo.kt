package com.uri.lee.dl.core.common

/** The running build, supplied by each app's entry point. */
data class AppInfo(
    val versionName: String,
    val versionCode: Long,
    /** "android" or "ios": names platform-specific Remote Config keys and the user agent. */
    val platform: String,
    val isDebug: Boolean,
    /** The photo-upload Worker (workers/photo-upload); empty when the build has none. */
    val photoUploadUrl: String,
)
