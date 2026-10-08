package com.uri.lee.dl.core.ml

import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.ml.ImageEmbedder
import com.uri.lee.dl.domain.ml.ImageEmbedderLoader
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.Float32Array
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import org.w3c.fetch.Response
import kotlin.js.json

/**
 * A backbone in LiteRT.js (WebAssembly): 224 × 224 RGB in [0, 1], as the MediaPipe image
 * embedders expect; the first output is the embedding.
 */
internal class LiteRtEmbedder(private val model: dynamic) : ImageEmbedder {

    override suspend fun embed(image: ClassifierImage): FloatArray = jsErrorsAsExceptions {
        val canvas = (image as WebClassifierImage).canvas
        val pixels = modelInput(canvas, SIZE)
            .context2d.getImageData(0.0, 0.0, SIZE.toDouble(), SIZE.toDouble()).data
        val input = Float32Array(SIZE * SIZE * 3)
        var j = 0
        for (i in 0 until SIZE * SIZE * 4 step 4) {
            for (c in 0 until 3) input[j++] = (pixels[i + c].toInt() and 0xff) / 255f
        }
        val outputs = settled(model.run(LiteRt.Tensor(input, arrayOf(1, SIZE, SIZE, 3))))
        val output = outputs[0]
        val values = settled(output.toTypedArray()).unsafeCast<Float32Array>()
        output.delete()
        FloatArray(values.length) { values[it] }
    }

    override fun close() {
        model.delete()
    }

    private companion object {
        const val SIZE = 224
    }
}

internal class LiteRtEmbedderLoader : ImageEmbedderLoader {
    override suspend fun load(model: String): ImageEmbedder = jsErrorsAsExceptions {
        loadLiteRtRuntime()
        val response = window.fetch(model).await()
        check(response.ok) { "HTTP ${response.status} for $model" }
        val bytes = response.unsafeCast<Response>().arrayBuffer().await()
        LiteRtEmbedder(LiteRt.loadAndCompile(Uint8Array(bytes), json("accelerator" to "wasm")).await())
    }
}
