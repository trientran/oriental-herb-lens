package com.uri.lee.dl.domain.training

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
 * Files in the app's own storage, by relative name ("models/abc/meta.txt"): kept across launches,
 * not shown to the user. Android: internal files; iOS: Application Support; web: the Origin
 * Private File System.
 */
interface AppFiles {
    suspend fun read(name: String): ByteArray?
    suspend fun write(name: String, bytes: ByteArray)
    suspend fun append(name: String, text: String)
    /** Deletes a file or a folder and everything in it. */
    suspend fun delete(name: String)
    /** Names of the files and folders directly in [folder] (empty if it doesn't exist). */
    suspend fun list(folder: String): List<String>
    /** A location the embedder loader can open for an existing file: a path, or a URL on the web. */
    suspend fun location(name: String): String
}

/** The same files, under [prefix]: "work/x" here is "[prefix]/work/x" in the app's storage. */
fun AppFiles.scoped(prefix: String): AppFiles = object : AppFiles {
    private fun full(name: String) = "$prefix/$name"
    override suspend fun read(name: String) = this@scoped.read(full(name))
    override suspend fun write(name: String, bytes: ByteArray) = this@scoped.write(full(name), bytes)
    override suspend fun append(name: String, text: String) = this@scoped.append(full(name), text)
    override suspend fun delete(name: String) = this@scoped.delete(full(name))
    override suspend fun list(folder: String) = this@scoped.list(full(folder))
    override suspend fun location(name: String) = this@scoped.location(full(name))
}

/**
 * The image backbones user-trained models build on (MediaPipe's MobileNet embedders), downloaded
 * once when first needed and then kept.
 */
interface Backbones {
    /** Backbone names, e.g. "mobilenet_v3_large". */
    val available: List<String>

    /** Where the backbone is, downloading it first if needed ([onDownloading] says so). */
    suspend fun location(name: String, onDownloading: () -> Unit = {}): String

    /** The backbone file itself (to build a standalone .tflite from). */
    suspend fun read(name: String): ByteArray
}
