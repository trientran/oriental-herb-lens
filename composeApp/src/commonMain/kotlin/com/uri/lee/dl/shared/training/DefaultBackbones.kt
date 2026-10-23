package com.uri.lee.dl.shared.training

import com.uri.lee.dl.domain.training.AppFiles
import com.uri.lee.dl.domain.training.Backbones
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get

/** Backbones copied in by hand on a developer's device (adb, devicectl, the local test server). */
interface LocalBackbones {
    /** Name to a location the embedder loader can open. */
    suspend fun locations(): Map<String, String>
    suspend fun read(location: String): ByteArray
}

/**
 * The image backbones, downloaded the first time they're used and kept in the app's files; a
 * hand-copied one is used instead if there. MediaPipe's embedders (Apache 2.0) come from Google's
 * public model storage; DINOv2 ViT-S/14 (Apache 2.0; Oquab et al., 2023), converted to LiteRT with
 * 8-bit weights (on-device-training-paper/scripts/convert_dinov2.py), from the app's R2 bucket.
 */
class DefaultBackbones(private val files: AppFiles, private val http: HttpClient, private val local: LocalBackbones?) : Backbones {

    override val available: List<String> = DOWNLOADS.keys.toList()

    override suspend fun location(name: String, onDownloading: (received: Long, total: Long?) -> Unit): String {
        local?.locations()?.get(name)?.let { return it }
        val file = file(name)
        if (files.read(file) == null) {
            val url = DOWNLOADS[name] ?: error("No backbone called $name")
            onDownloading(0, null)
            var reported = 0L
            val bytes = http.get(url) {
                onDownload { received, total ->
                    // Every 256 KB is plenty for a progress bar
                    if (received - reported >= PROGRESS_STEP || received == total) {
                        reported = received
                        onDownloading(received, total)
                    }
                }
            }.body<ByteArray>()
            files.write(file, bytes)
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
            "dinov2_vits14" to "https://pub-394936ea0f444a64a542ff743b102f1f.r2.dev/backbones/dinov2_vits14_w8-20261019-9c19c332.tflite",
        )
        private const val PROGRESS_STEP = 256 * 1024L
    }
}
