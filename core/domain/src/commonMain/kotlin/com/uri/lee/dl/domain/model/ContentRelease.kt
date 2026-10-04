package com.uri.lee.dl.domain.model

/** A published content file: where to download it and the SHA-256 (hex) it must match. */
data class ContentRelease(val url: String, val sha256: String)

sealed interface InstallResult {
    data object Installed : InstallResult

    /** Valid but can't be activated yet, e.g. a model whose labels the current catalog lacks. */
    data class Deferred(val reason: String) : InstallResult

    /** [retryable] failures (network) are worth retrying later; the others need a new release. */
    data class Failed(val reason: String, val retryable: Boolean) : InstallResult
}
