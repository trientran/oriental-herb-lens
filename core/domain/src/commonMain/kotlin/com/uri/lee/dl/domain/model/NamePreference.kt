package com.uri.lee.dl.domain.model

/** Whether species are named in Vietnamese first, as [Species.displayName] does; decided by the app. */
fun interface NamePreference {
    fun vietnameseFirst(): Boolean
}
