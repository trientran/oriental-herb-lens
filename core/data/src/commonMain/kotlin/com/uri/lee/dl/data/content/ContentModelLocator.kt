package com.uri.lee.dl.data.content

import com.uri.lee.dl.core.ml.HerbModelFile
import com.uri.lee.dl.core.ml.HerbModelLocator
import com.uri.lee.dl.domain.model.ContentKind
import okio.FileSystem

/** The downloaded model when one is installed, otherwise the bundled copy. */
internal class ContentModelLocator(
    private val files: ContentFiles,
    private val fileSystem: FileSystem,
) : HerbModelLocator {
    override fun current(): HerbModelFile {
        val installed = files.installed(ContentKind.MODEL)
        val meta = fileSystem.metadataOrNull(installed)
        val size = meta?.size ?: 0L
        return if (meta != null && meta.isRegularFile && size > 0) {
            HerbModelFile.Installed(installed.toString(), cacheKey = "installed-$size-${meta.lastModifiedAtMillis}")
        } else {
            HerbModelFile.Bundled(ContentFiles.fileName(ContentKind.MODEL))
        }
    }
}
