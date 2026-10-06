package com.uri.lee.dl.core.ml

import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.ml.ImageEmbedder
import com.uri.lee.dl.domain.ml.ImageEmbedderLoader
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSFileManager
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A backbone through the Swift LiteRT bridge ([NativeEmbedder]). */
internal class IosEmbedder(private val native: NativeEmbedder, private val modelPath: String) : ImageEmbedder {

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun embed(image: ClassifierImage): FloatArray = suspendCancellableCoroutine { continuation ->
        native.embed((image as IosClassifierImage).image, modelPath) { data, error ->
            if (data == null) {
                continuation.resumeWithException(IllegalStateException(error ?: "Embedding failed"))
            } else {
                val values = FloatArray((data.length / 4u).toInt())
                if (values.isNotEmpty()) values.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
                continuation.resume(values)
            }
        }
    }

    override fun close() = native.release(modelPath)
}

/** [model] is a file path. */
internal class IosEmbedderLoader(private val native: NativeEmbedder) : ImageEmbedderLoader {
    override suspend fun load(model: String): ImageEmbedder {
        check(NSFileManager.defaultManager.fileExistsAtPath(model)) { "No model at $model" }
        return IosEmbedder(native, model)
    }
}
