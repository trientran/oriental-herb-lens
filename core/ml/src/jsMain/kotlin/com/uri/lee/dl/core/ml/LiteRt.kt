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
    class Tensor(data: Float32Array, shape: Array<Int>)
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

/** LiteRT.js returns some results directly and others as promises, depending on the backend. */
internal suspend fun settled(value: dynamic): dynamic = js("Promise").resolve(value).unsafeCast<Promise<dynamic>>().await()
