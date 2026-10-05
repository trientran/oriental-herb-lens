package com.uri.lee.dl.data.catalog

import com.uri.lee.dl.data.content.ContentFiles
import com.uri.lee.dl.domain.model.ContentKind
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSBundle

/** The installed catalog if content sync has downloaded one, otherwise the copy in the app bundle. */
internal class IosCatalogSource(
    private val files: ContentFiles,
    private val fileSystem: FileSystem,
) : CatalogSource {

    override fun readText(): String {
        val installed = files.installed(ContentKind.CATALOG)
        return if (fileSystem.exists(installed)) fileSystem.read(installed) { readUtf8() } else readBundledText()
    }

    override fun readBundledText(): String {
        val name = ContentFiles.fileName(ContentKind.CATALOG)
        val path = NSBundle.mainBundle.pathForResource(name.substringBeforeLast('.'), name.substringAfterLast('.'))
            ?: error("$name is missing from the app bundle")
        return fileSystem.read(path.toPath()) { readUtf8() }
    }
}
