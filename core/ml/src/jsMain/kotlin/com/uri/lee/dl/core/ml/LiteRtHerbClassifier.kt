package com.uri.lee.dl.core.ml

import co.touchlab.kermit.Logger
import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.ml.HerbClassifier
import com.uri.lee.dl.domain.model.Classification
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Float32Array
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import org.w3c.fetch.Response
import kotlin.js.Promise
import kotlin.js.json

@JsModule("@litertjs/core")
@JsNonModule
private external object LiteRt {
    fun loadLiteRt(wasmDirectory: String): Promise<dynamic>
    fun loadAndCompile(model: dynamic, options: dynamic): Promise<dynamic>
    class Tensor(data: Float32Array, shape: Array<Int>)
}

/** Where the browser gets the herb model: the published release, or the copy served with the site. */
fun interface WebModelSource {
    suspend fun modelUrl(): String
}

/**
 * The herb model in LiteRT.js (WebAssembly), with the preprocessing ML Kit does from the model's
 * metadata on the phones: 224 × 224 RGB, normalised per channel. Labels come from the model's
 * embedded labels.txt.
 */
internal class LiteRtHerbClassifier(private val source: WebModelSource) : HerbClassifier {

    private class Model(val compiled: dynamic, val labels: List<String>)

    private val mutex = Mutex()
    private var model: Model? = null

    override suspend fun classify(image: ClassifierImage, minConfidence: Float, maxResults: Int): List<Classification> =
        jsErrorsAsExceptions { run(image, minConfidence, maxResults) }

    private suspend fun run(image: ClassifierImage, minConfidence: Float, maxResults: Int): List<Classification> {
        val model = model()
        val canvas = (image as WebClassifierImage).canvas
        val pixels = drawn(canvas, 0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble(), SIZE, SIZE)
            .context2d.getImageData(0.0, 0.0, SIZE.toDouble(), SIZE.toDouble()).data
        val input = Float32Array(SIZE * SIZE * 3)
        var j = 0
        for (i in 0 until SIZE * SIZE * 4 step 4) {
            for (c in 0 until 3) input[j++] = ((pixels[i + c].toInt() and 0xff) - MEAN[c]) / STD[c]
        }
        val outputs = settled(model.compiled.run(LiteRt.Tensor(input, arrayOf(1, SIZE, SIZE, 3))))
        val output = outputs[0]
        val scores = settled(output.toTypedArray()).unsafeCast<Float32Array>()
        output.delete()
        return (0 until scores.length)
            .filter { scores[it] >= minConfidence && it < model.labels.size }
            .sortedByDescending { scores[it] }
            .take(maxResults)
            .map { Classification(model.labels[it], scores[it]) }
    }

    /** LiteRT.js returns some results directly and others as promises, depending on the backend. */
    private suspend fun settled(value: dynamic): dynamic = js("Promise").resolve(value).unsafeCast<Promise<dynamic>>().await()

    private suspend fun model(): Model = model ?: mutex.withLock {
        model ?: load().also { model = it }
    }

    private suspend fun load(): Model {
        LiteRt.loadLiteRt(WASM_DIRECTORY).await()
        val url = source.modelUrl()
        val bytes = window.fetch(url).await().also { check(it.ok) { "HTTP ${it.status} for $url" } }
            .unsafeCast<Response>().arrayBuffer().await()
        val compiled = LiteRt.loadAndCompile(Uint8Array(bytes), json("accelerator" to "wasm")).await()
        val labels = storedZipEntry(Uint8Array(bytes), "labels.txt")?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: error("The model has no labels.txt")
        Logger.withTag("Model").i { "Loaded $url: ${labels.size} labels" }
        return Model(compiled, labels)
    }

    private companion object {
        const val SIZE = 224
        val MEAN = floatArrayOf(123.675f, 116.28f, 103.53f)
        val STD = floatArrayOf(58.395f, 57.12f, 57.375f)

        /** LiteRT.js's WebAssembly runtime, copied next to index.html by the webApp build. */
        const val WASM_DIRECTORY = "litert/"
    }
}

/**
 * Errors thrown by browser APIs (a failed fetch, LiteRT.js) are JS errors, not Exceptions, so the
 * callers' `catch (e: Exception)` would miss them.
 */
internal suspend fun <T> jsErrorsAsExceptions(block: suspend () -> T): T = try {
    block()
} catch (e: Throwable) {
    if (e is Exception || e is Error) throw e
    throw IllegalStateException(e.message ?: "Browser error", e)
}

/**
 * Reads an uncompressed ("stored") entry from the zip archive TFLite metadata appends to a model,
 * the way core:data reads it with Okio on the phones.
 */
internal fun storedZipEntry(bytes: Uint8Array, name: String): String? {
    fun u16(at: Int) = (bytes[at].toInt() and 0xff) or ((bytes[at + 1].toInt() and 0xff) shl 8)
    fun u32(at: Int) = u16(at) or (u16(at + 2) shl 16)
    // End of central directory: the last "PK\u0005\u0006"
    val end = (bytes.length - 22 downTo maxOf(0, bytes.length - 65_557)).firstOrNull { u32(it) == 0x06054b50 } ?: return null
    var entry = u32(end + 16)
    repeat(u16(end + 10)) {
        val nameLength = u16(entry + 28)
        val entryName = (0 until nameLength).map { (bytes[entry + 46 + it].toInt() and 0xff).toChar() }.joinToString("")
        if (entryName == name) {
            if (u16(entry + 10) != 0) return null // compressed: not what the metadata writer produces
            val size = u32(entry + 20)
            val local = u32(entry + 42)
            val start = local + 30 + u16(local + 26) + u16(local + 28)
            return Uint8Array(bytes.buffer, bytes.byteOffset + start, size).unsafeCast<ByteArray>().decodeToString()
        }
        entry += 46 + nameLength + u16(entry + 30) + u16(entry + 32)
    }
    return null
}
