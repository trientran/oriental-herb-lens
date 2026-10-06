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
 * Files in the app's own storage, by relative name ("work/done.txt"): where a run keeps its
 * progress so it can resume, and where downloaded backbones are kept.
 */
interface ResearchFiles {
    suspend fun read(name: String): ByteArray?
    suspend fun write(name: String, bytes: ByteArray)
    suspend fun append(name: String, text: String)
    /** Deletes a file or a folder and everything in it. */
    suspend fun delete(name: String)
    /** A location the embedder loader can open for an existing file: a path, or a URL on the web. */
    suspend fun location(name: String): String
}

/** Keeps a run going with the screen off or the app in the background, as far as the platform allows. */
interface ResearchBackground {
    /** A run starts; [onExpired] is called if the system ends the background time (the run is stopped, to resume later). */
    fun start(title: String, onExpired: () -> Unit) {}
    fun progress(done: Int, total: Int, text: String) {}
    fun stop() {}
}

/**
 * What each platform provides for research runs (plan Phase 7): device details and readings,
 * storage, dataset pickers, saving the results archive and running in the background.
 */
class ResearchPlatform(
    /** e.g. "Xiaomi Redmi Note 12 (Android 14, SM6225)". */
    val device: String,
    /** "android", "ios" or "web". */
    val platform: String,
    val monitor: ResourceMonitor,
    val files: ResearchFiles,
    /** Backbones copied in by hand (adb, devicectl, the local test server): name to location. */
    val localBackbones: suspend () -> Map<String, String> = { emptyMap() },
    /** Reads a backbone at a location from [localBackbones] or [ResearchFiles.location]. */
    val readModel: suspend (location: String) -> ByteArray,
    /** Lets the user choose a folder with one subfolder of photos per class; null if cancelled. */
    val pickDatasetFolder: suspend () -> Dataset?,
    /** The same from a .zip, where the platform can open one. */
    val pickDatasetZip: (suspend () -> Dataset?)? = null,
    /**
     * Datasets copied into the app's own folder (adb, devicectl, Finder), one folder each in
     * datasets/: no picker needed, which suits long runs prepared from a computer.
     */
    val appDatasets: suspend () -> List<Dataset> = { emptyList() },
    /** Hands the results archive to the user (save dialog, share sheet or download). */
    val saveArchive: suspend (fileName: String, bytes: ByteArray) -> Unit,
    /** Keeps the screen on during a run. */
    val keepAwake: (Boolean) -> Unit,
    val background: ResearchBackground = object : ResearchBackground {},
    /** Shown on the screen: what happens if the screen goes off or the app is left. */
    val backgroundNote: String = "",
)
