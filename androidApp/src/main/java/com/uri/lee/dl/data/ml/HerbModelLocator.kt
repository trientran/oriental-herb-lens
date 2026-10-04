package com.uri.lee.dl.data.ml

import com.uri.lee.dl.data.content.ContentFiles
import com.uri.lee.dl.domain.model.ContentKind
import java.io.File

/** Which model file to load: a downloaded one when installed, otherwise the copy bundled in assets. */
sealed interface HerbModelFile {
    /** Changes whenever the model file changes, so cached interpreters can be rebuilt. */
    val cacheKey: String

    data object Bundled : HerbModelFile {
        override val cacheKey = "bundled"
        val assetPath = ContentFiles.fileName(ContentKind.MODEL)
    }

    data class Installed(val file: File) : HerbModelFile {
        override val cacheKey = "installed-${file.length()}-${file.lastModified()}"
    }
}

class HerbModelLocator(private val files: ContentFiles) {
    fun current(): HerbModelFile =
        files.installed(ContentKind.MODEL)
            .takeIf { it.isFile && it.length() > 0 }
            ?.let { HerbModelFile.Installed(it) }
            ?: HerbModelFile.Bundled
}
