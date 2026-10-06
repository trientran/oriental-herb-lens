package com.uri.lee.dl.data.content

import com.uri.lee.dl.domain.model.ContentKind
import okio.Path

/**
 * Downloaded content lives in app storage (never the cache, which the OS may clear) under the
 * same file names as the bundled copies. A newer version replaces the file in place.
 */
internal class ContentFiles(appFilesDir: Path) {
    val directory: Path = appFilesDir / "content"

    fun installed(kind: ContentKind): Path = directory / fileName(kind)

    /** Where a download is written and checked before it replaces [installed]. */
    fun staging(kind: ContentKind): Path = directory / (fileName(kind) + ".download")

    companion object {
        fun fileName(kind: ContentKind) = when (kind) {
            ContentKind.MODEL -> "herb_model.tflite"
            ContentKind.CATALOG -> "herb_catalog.csv"
        }
    }
}
