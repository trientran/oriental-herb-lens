package com.uri.lee.dl.shared.training

import com.uri.lee.dl.domain.training.AppFiles
import com.uri.lee.dl.domain.training.Backbones
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/** Backbones copied in by hand on a developer's device (adb, devicectl, the local test server). */
interface LocalBackbones {
    /** Name to a location the embedder loader can open. */
    suspend fun locations(): Map<String, String>
    suspend fun read(location: String): ByteArray
}

/**
 * MediaPipe's image embedders (Apache 2.0), downloaded from Google's public model storage the
 * first time they're used and kept in the app's files; a hand-copied one is used instead if there.
 */
class DefaultBackbones(private val files: AppFiles, private val http: HttpClient, private val local: LocalBackbones?) : Backbones {

    override val available: List<String> = DOWNLOADS.keys.toList()

    override suspend fun location(name: String, onDownloading: () -> Unit): String {
        local?.locations()?.get(name)?.let { return it }
        val file = file(name)
        if (files.read(file) == null) {
            val url = DOWNLOADS[name] ?: error("No backbone called $name")
            onDownloading()
            files.write(file, http.get(url).body<ByteArray>())
        }
        return files.location(file)
    }

    override suspend fun read(name: String): ByteArray {
        local?.locations()?.get(name)?.let { return local.read(it) }
        location(name)
        return files.read(file(name)) ?: error("Couldn't read $name")
    }

    private fun file(name: String) = "backbones/$name.tflite"

    companion object {
        val DOWNLOADS = mapOf(
            "mobilenet_v3_small" to "https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_small/float32/latest/mobilenet_v3_small.tflite",
            "mobilenet_v3_large" to "https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_large/float32/latest/mobilenet_v3_large.tflite",
        )
    }
}
