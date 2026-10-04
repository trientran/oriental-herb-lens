package com.uri.lee.dl.data.content

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.openZip

/** A .tflite model with metadata is also a zip; its label list is the embedded `labels.txt`. */
internal object ModelLabels {
    /** The model's labels, or null if the file carries no label list. */
    fun read(fileSystem: FileSystem, model: Path): List<String>? = try {
        val zip = fileSystem.openZip(model)
        val labels = "/labels.txt".toPath()
        if (!zip.exists(labels)) null
        else zip.read(labels) { readUtf8() }.lines().map { it.trim() }.filter { it.isNotEmpty() }
    } catch (e: Exception) {
        null // not a zip: a model without metadata
    }
}
