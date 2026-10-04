package com.uri.lee.dl.data.catalog

import android.content.res.AssetManager
import com.uri.lee.dl.data.content.ContentFiles
import com.uri.lee.dl.domain.model.ContentKind

/** Supplies the raw catalog CSV. */
fun interface CatalogSource {
    fun readText(): String

    /** Falls back to the bundled catalog, so a missing or unreadable download never leaves the app without names. */
    fun readBundledText(): String = readText()
}

class AndroidCatalogSource(
    private val assets: AssetManager,
    private val files: ContentFiles,
) : CatalogSource {

    override fun readText(): String {
        val installed = files.installed(ContentKind.CATALOG)
        return if (installed.isFile) installed.readText() else readBundledText()
    }

    override fun readBundledText(): String =
        assets.open(ContentFiles.fileName(ContentKind.CATALOG)).bufferedReader().use { it.readText() }
}
