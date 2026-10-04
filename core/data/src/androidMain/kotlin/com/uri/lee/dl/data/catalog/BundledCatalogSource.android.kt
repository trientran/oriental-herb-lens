package com.uri.lee.dl.data.catalog

import android.content.res.AssetManager
import com.uri.lee.dl.data.content.ContentFiles
import com.uri.lee.dl.domain.model.ContentKind
import okio.FileSystem

/** The installed catalog if content sync has downloaded one, otherwise the copy in the app's assets. */
internal class AndroidCatalogSource(
    private val assets: AssetManager,
    private val files: ContentFiles,
    private val fileSystem: FileSystem,
) : CatalogSource {

    override fun readText(): String {
        val installed = files.installed(ContentKind.CATALOG)
        return if (fileSystem.exists(installed)) fileSystem.read(installed) { readUtf8() } else readBundledText()
    }

    override fun readBundledText(): String =
        assets.open(ContentFiles.fileName(ContentKind.CATALOG)).bufferedReader().use { it.readText() }
}
