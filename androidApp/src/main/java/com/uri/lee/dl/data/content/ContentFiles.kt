package com.uri.lee.dl.data.content

import com.uri.lee.dl.domain.model.ContentKind
import java.io.File

/**
 * Downloaded content lives in app storage (never the cache, which the OS may clear) under the
 * same file names as the bundled copies in assets. A newer version replaces the file in place.
 */
class ContentFiles(filesDir: File) {
    val directory = File(filesDir, "content")

    fun installed(kind: ContentKind) = File(directory, fileName(kind))

    /** Where a download is written and checked before it replaces [installed]. */
    fun staging(kind: ContentKind) = File(directory, fileName(kind) + ".download")

    companion object {
        fun fileName(kind: ContentKind) = when (kind) {
            ContentKind.MODEL -> "herb_model.tflite"
            ContentKind.CATALOG -> "herb_catalog.csv"
        }
    }
}
