package com.uri.lee.dl.shared.research

import com.uri.lee.dl.core.training.ResourceMonitor
import com.uri.lee.dl.domain.media.LocalImage

/** A photo and the class (species) it belongs to. */
class LabelledImage(val className: String, val image: LocalImage)

/** A training dataset: photos sorted into one folder per class. */
class Dataset(val name: String, val images: List<LabelledImage>) {
    val classes: List<String> = images.map { it.className }.distinct().sorted()

    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "gif")

        /**
         * A dataset from files given by their path inside the chosen folder or zip
         * ("Lantana camara/001.jpg"). The class is the folder a photo is in; a single folder
         * wrapping everything ("weeds/Lantana camara/001.jpg") is looked through. Other files,
         * hidden ones and photos not in a class folder are left out.
         */
        fun fromPaths(name: String, files: List<Pair<String, LocalImage>>): Dataset {
            val photos = files.mapNotNull { (path, image) ->
                val parts = path.split('/', '\\').filter { it.isNotEmpty() }
                val fileName = parts.lastOrNull() ?: return@mapNotNull null
                val hidden = parts.any { it.startsWith('.') || it == "__MACOSX" }
                if (hidden || fileName.substringAfterLast('.', "").lowercase() !in IMAGE_EXTENSIONS) null else parts to image
            }
            val wrapped = photos.isNotEmpty() && photos.all { it.first.size >= 3 } && photos.map { it.first.first() }.distinct().size == 1
            val images = photos.mapNotNull { (parts, image) ->
                val inner = if (wrapped) parts.drop(1) else parts
                if (inner.size < 2) null else LabelledImage(inner[inner.size - 2], image)
            }
            return Dataset(name, images)
        }
    }
}

/**
 * What each platform provides for research runs (plan Phase 7): device details and readings,
 * dataset pickers, backbones, and saving the results archive.
 */
class ResearchPlatform(
    /** e.g. "Xiaomi Redmi Note 12 (Android 14, SM6225)". */
    val device: String,
    /** "android", "ios" or "web". */
    val platform: String,
    val monitor: ResourceMonitor,
    /** Backbone name to a location the embedder loader and [readModel] can open. */
    val backbones: suspend () -> Map<String, String>,
    val readModel: suspend (location: String) -> ByteArray,
    /** Lets the user choose a folder with one subfolder of photos per class; null if cancelled. */
    val pickDatasetFolder: suspend () -> Dataset?,
    /** The same from a .zip, where the platform can open one. */
    val pickDatasetZip: (suspend () -> Dataset?)? = null,
    /**
     * Datasets copied into the app's own folder (adb, devicectl), one folder each in datasets/:
     * no picker needed, which suits long runs prepared from a computer.
     */
    val appDatasets: suspend () -> List<Dataset> = { emptyList() },
    /** Hands the results archive to the user (save dialog, share sheet or download). */
    val saveArchive: suspend (fileName: String, bytes: ByteArray) -> Unit,
    /** Keeps the screen on during a long run, so the device doesn't sleep. */
    val keepAwake: (Boolean) -> Unit,
)
