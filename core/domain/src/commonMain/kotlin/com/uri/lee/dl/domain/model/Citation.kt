package com.uri.lee.dl.domain.model

/** A work to cite when the app is used in research: a published paper, or the app itself. */
data class Citation(
    /** The full reference, ready to paste into a bibliography. */
    val text: String,
    /** Where the work can be read, such as its DOI link; null when there is none. */
    val url: String? = null,
)
