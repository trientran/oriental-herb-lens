package com.uri.lee.dl.data.catalog

/** Supplies the raw catalog CSV. */
fun interface CatalogSource {
    fun readText(): String

    /** Falls back to the bundled catalog, so a missing or unreadable download never leaves the app without names. */
    fun readBundledText(): String = readText()
}
