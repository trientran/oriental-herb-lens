package com.uri.lee.dl.core.ml

import kotlinx.coroutines.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.khronos.webgl.Float32Array
import kotlin.js.Promise

@JsModule("@litertjs/core")
@JsNonModule
internal external object LiteRt {
    fun loadLiteRt(wasmDirectory: String): Promise<dynamic>
    fun loadAndCompile(model: dynamic, options: dynamic): Promise<dynamic>
    class Tensor(data: Float32Array, shape: Array<Int>) {
        /** Frees its WebAssembly memory, which JavaScript's garbage collector doesn't. */
        fun delete()
    }
}

/** LiteRT.js's WebAssembly runtime, copied next to index.html by the webApp build. */
private const val WASM_DIRECTORY = "litert/"

private val runtimeMutex = Mutex()
private var runtimeLoaded = false

/** Loads LiteRT.js's runtime once per page, whichever model asks first. */
internal suspend fun loadLiteRtRuntime() = runtimeMutex.withLock {
    if (!runtimeLoaded) {
        LiteRt.loadLiteRt(WASM_DIRECTORY).await()
        runtimeLoaded = true
    }
}

/**
 * Runs [model] on one [input] tensor and returns its first output's values. Frees the input and
 * every output afterwards: their WebAssembly memory isn't garbage-collected, and kept tensors
 * fill the WebAssembly heap after a few thousand photos (a research run embeds thousands).
 */
internal suspend fun runOnce(model: dynamic, input: Float32Array, shape: Array<Int>): Float32Array {
    val tensor = LiteRt.Tensor(input, shape)
    var outputs: dynamic = null
    try {
        outputs = settled(model.run(tensor))
        return settled(outputs[0].toTypedArray()).unsafeCast<Float32Array>()
    } finally {
        tensor.delete()
        if (outputs != null) for (i in 0 until (outputs.length as Int)) outputs[i].delete()
    }
}

/** LiteRT.js returns some results directly and others as promises, depending on the backend. */
internal suspend fun settled(value: dynamic): dynamic = js("Promise").resolve(value).unsafeCast<Promise<dynamic>>().await()
