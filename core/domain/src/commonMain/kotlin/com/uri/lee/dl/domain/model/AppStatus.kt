package com.uri.lee.dl.domain.model

enum class UpdatePolicy { NONE, RECOMMENDED, REQUIRED }

/** App-wide conditions the main screen must react to. */
data class AppStatus(
    val update: UpdatePolicy = UpdatePolicy.NONE,
    /** Service switched off remotely (the "stack overflow" notice). */
    val isSuspended: Boolean = false,
)
