package com.uri.lee.dl.shared

import com.uri.lee.dl.core.ml.WebLocalImage
import com.uri.lee.dl.core.training.ResourceMonitor
import com.uri.lee.dl.core.training.ResourceSample
import com.uri.lee.dl.shared.research.Dataset
import com.uri.lee.dl.shared.research.ResearchPlatform
import kotlin.js.Promise
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.await
import org.khronos.webgl.Int8Array
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.url.URL
import org.w3c.fetch.Response
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

/**
 * The web's side of research mode (plan Phase 7), on a developer's machine: backbones come from
 * the local test-photo server (see docs/user-trained-models.md), datasets from a chosen folder.
 */
internal fun webResearch(): ResearchPlatform {
    val battery = WebBattery()
    var wakeLock: dynamic = null
    return ResearchPlatform(
        device = window.navigator.userAgent,
        platform = "web",
        monitor = ResourceMonitor {
            val memory = window.performance.asDynamic().memory
            ResourceSample(
                // Chrome only: the JS heap, not the WebAssembly runtime's memory
                memoryBytes = (memory?.usedJSHeapSize as? Number)?.toLong(),
                batteryPercent = battery.percent,
                charging = battery.charging,
            )
        },
        backbones = {
            BACKBONES.associateWith { "$LOCAL_SERVER/backbones/$it.tflite" }.filterValues { url ->
                runCatching { fetch(url, kotlin.js.json("method" to "HEAD")).ok }.getOrDefault(false)
            }
        },
        readModel = { url ->
            val response = fetch(url)
            check(response.ok) { "HTTP ${response.status} for $url" }
            Int8Array(response.arrayBuffer().await()).unsafeCast<ByteArray>()
        },
        pickDatasetFolder = ::pickFolder,
        saveArchive = { name, bytes ->
            val url = URL.createObjectURL(Blob(arrayOf(bytes), BlobPropertyBag(type = "application/zip")))
            val link = document.createElement("a") as HTMLAnchorElement
            link.href = url
            link.download = name
            link.click()
            window.setTimeout({ URL.revokeObjectURL(url) }, 60_000)
        },
        keepAwake = { on ->
            val locks = window.navigator.asDynamic().wakeLock
            if (on && locks != null) {
                locks.request("screen").then({ sentinel: dynamic -> wakeLock = sentinel }, { _: dynamic -> })
            } else if (!on && wakeLock != null) {
                wakeLock.release()
                wakeLock = null
            }
        },
    )
}

// A plain object for the options: Kotlin's RequestInit would send nulls the browser rejects
private suspend fun fetch(url: String, init: dynamic = js("({})")): Response =
    window.asDynamic().fetch(url, init).unsafeCast<Promise<Response>>().await()

/** A folder chooser: every file inside comes with its path, e.g. "weeds/Lantana camara/001.jpg". */
private suspend fun pickFolder(): Dataset? {
    val result = CompletableDeferred<Dataset?>()
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.asDynamic().webkitdirectory = true
    input.onchange = {
        val files = input.files
        val list = (0 until (files?.length ?: 0)).mapNotNull { files?.item(it) }
        val name = list.firstOrNull()?.asDynamic()?.webkitRelativePath?.unsafeCast<String>()?.substringBefore('/') ?: "dataset"
        result.complete(Dataset.fromPaths(name, list.map { (it.asDynamic().webkitRelativePath as String) to WebLocalImage(it) }))
    }
    input.addEventListener("cancel", { result.complete(null) })
    input.click()
    return result.await()
}

/** The Battery Status API (Chrome); values stay null where the browser doesn't offer it. */
private class WebBattery {
    var percent: Int? = null
        private set
    var charging: Boolean? = null
        private set

    init {
        val getBattery = window.navigator.asDynamic().getBattery
        if (getBattery != null) {
            window.navigator.asDynamic().getBattery().unsafeCast<Promise<dynamic>>().then { manager: dynamic ->
                fun read() {
                    percent = ((manager.level as Double) * 100).toInt()
                    charging = manager.charging as Boolean
                }
                read()
                manager.addEventListener("levelchange", { read() })
                manager.addEventListener("chargingchange", { read() })
            }
        }
    }
}

private const val LOCAL_SERVER = "http://127.0.0.1:8767"
private val BACKBONES = listOf("mobilenet_v3_small", "mobilenet_v3_large")
