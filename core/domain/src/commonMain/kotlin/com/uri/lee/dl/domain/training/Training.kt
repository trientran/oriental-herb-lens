package com.uri.lee.dl.domain.training

import com.uri.lee.dl.domain.media.LocalImage

/** A photo and the class (species) it belongs to; [stage] is the folder its class folder is in, if any. */
class LabelledImage(val className: String, val image: LocalImage, val stage: String? = null)

/** A training dataset: photos sorted into one folder per class. */
class Dataset(val name: String, val images: List<LabelledImage>) {
    val classes: List<String> = images.map { it.className }.distinct().sorted()

    /**
     * The stages classes arrive in, for class-incremental runs in a set order: the class folders
     * are grouped into two or more stage folders ("1 Vietnam/Mimosa pigra/…", "2 Australia/…"),
     * learnt in the order of their names (leading numbers compared as numbers). Null unless every
     * photo is in a stage folder and each class is in exactly one.
     */
    val stages: List<String>? = run {
        if (images.isEmpty() || images.any { it.stage == null }) return@run null
        val stageOf = images.groupBy { it.className }.mapValues { (_, photos) -> photos.map { it.stage!! }.distinct() }
        if (stageOf.values.any { it.size != 1 }) return@run null
        stageOf.values.map { it.single() }.distinct().takeIf { it.size >= 2 }?.let(::sortStages)
    }

    /** The index in [stages] of [className]'s stage. */
    fun stageOf(className: String): Int? {
        val stages = stages ?: return null
        return images.firstOrNull { it.className == className }?.stage?.let(stages::indexOf)
    }

    companion object {
        /** Stage names in the order they're learnt: "2 Australia" before "10 Fiji" (a leading number counts as a number), then by name. */
        fun sortStages(names: Collection<String>): List<String> =
            names.sortedWith(compareBy<String>({ it.takeWhile(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }, { it }))

        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "gif")

        /**
         * A dataset from files given by their path inside the chosen folder or zip
         * ("Lantana camara/001.jpg"). The class is the folder a photo is in; a single folder
         * wrapping everything ("weeds/Lantana camara/001.jpg") is looked through, and the folder
         * a class folder is in is its stage (see [stages]). Other files, hidden ones and photos not
         * in a class folder are left out.
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
                if (inner.size < 2) null else LabelledImage(inner[inner.size - 2], image, inner.getOrNull(inner.size - 3))
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

    /**
     * Where the backbone is, downloading it first if needed; [onDownloading] reports the bytes
     * received so far and the file's size (null if the server doesn't say), from time to time.
     */
    suspend fun location(name: String, onDownloading: (received: Long, total: Long?) -> Unit = { _, _ -> }): String

    /** The backbone file itself (to build a standalone .tflite from). */
    suspend fun read(name: String): ByteArray
}
