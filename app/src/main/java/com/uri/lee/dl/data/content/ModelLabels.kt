package com.uri.lee.dl.data.content

import java.io.File
import java.util.zip.ZipFile

/** A .tflite model with metadata is also a zip; its label list is the embedded `labels.txt`. */
object ModelLabels {
    /** The model's labels, or null if the file carries no label list. */
    fun read(model: File): List<String>? = try {
        ZipFile(model).use { zip ->
            val entry = zip.getEntry("labels.txt") ?: return null
            zip.getInputStream(entry).bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() }
        }
    } catch (e: java.util.zip.ZipException) {
        null
    }
}
