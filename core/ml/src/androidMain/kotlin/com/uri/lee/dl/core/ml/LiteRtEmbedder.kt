package com.uri.lee.dl.core.ml

import android.graphics.Bitmap
import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.ml.ImageEmbedder
import com.uri.lee.dl.domain.ml.ImageEmbedderLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A backbone in LiteRT (user-trained models, plan Phase 7): 224 × 224 RGB in [0, 1], as the
 * MediaPipe image embedders expect; the first output is the embedding. ML Kit keeps the herb model.
 */
internal class LiteRtEmbedder(private val interpreter: Interpreter) : ImageEmbedder {
    private val input = ByteBuffer.allocateDirect(SIZE * SIZE * 3 * 4).order(ByteOrder.nativeOrder())
    private val pixels = IntArray(SIZE * SIZE)
    private val dimensions = interpreter.getOutputTensor(0).shape().last()
    private val mutex = Mutex()

    override suspend fun embed(image: ClassifierImage): FloatArray = mutex.withLock {
        withContext(Dispatchers.Default) {
            val bitmap = (image as MlKitClassifierImage).bitmap ?: error("No bitmap to embed")
            val scaled = Bitmap.createScaledBitmap(bitmap, SIZE, SIZE, true)
            scaled.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
            if (scaled !== bitmap) scaled.recycle()
            input.rewind()
            for (p in pixels) {
                input.putFloat((p shr 16 and 0xff) / 255f)
                input.putFloat((p shr 8 and 0xff) / 255f)
                input.putFloat((p and 0xff) / 255f)
            }
            input.rewind()
            val output = Array(1) { FloatArray(dimensions) }
            interpreter.run(input, output)
            output[0]
        }
    }

    override fun close() = interpreter.close()

    private companion object {
        const val SIZE = 224
    }
}

/** [model] is a file path. Four threads: most phones have at least four fast cores. */
internal class LiteRtEmbedderLoader : ImageEmbedderLoader {
    override suspend fun load(model: String): ImageEmbedder = withContext(Dispatchers.IO) {
        LiteRtEmbedder(Interpreter(File(model), Interpreter.Options().setNumThreads(4)))
    }
}
