package com.uri.lee.dl.shared

import com.uri.lee.dl.core.ml.WebLocalImage
import com.uri.lee.dl.core.training.ResourceMonitor
import com.uri.lee.dl.core.training.ResourceSample
import com.uri.lee.dl.domain.training.AppFiles
import com.uri.lee.dl.domain.training.Dataset
import com.uri.lee.dl.domain.training.scoped
import com.uri.lee.dl.feature.training.PickedFile
import com.uri.lee.dl.shared.training.LocalBackbones
import org.koin.mp.KoinPlatform
import com.uri.lee.dl.shared.research.ResearchPlatform
import kotlin.js.Promise
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.await
import org.khronos.webgl.Int8Array
import org.w3c.dom.HTMLAnchorElement
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
                foreground = document.asDynamic().visibilityState == "visible",
            )
        },
        files = KoinPlatform.getKoin().get<AppFiles>().scoped("research"),
        pickDatasetFolder = ::pickFolder,
        saveArchive = { name, bytes -> downloadFile(name, bytes, "application/zip") },
        backgroundNote = "Keep this tab open and in front, and the computer awake: browsers slow down background tabs and stop " +
            "when a tab closes. If it stops, open Research mode again and tap Resume: finished runs are kept in this browser.",
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

/** Files for user-trained models: a dataset folder, a model file, downloading a model. */
internal fun webFileActions() = FileActions(
    pickDatasetFolder = ::pickFolder,
    pickModelFile = ::pickModelFile,
    saveFile = { name, bytes -> downloadFile(name, bytes, "application/octet-stream") },
)

/** A .tflite chosen in the browser's file chooser. */
private suspend fun pickModelFile(): PickedFile? {
    val result = CompletableDeferred<org.w3c.files.File?>()
    chooseFiles({ accept = ".tflite" }) { result.complete(it.firstOrNull()) }
    val file = result.await() ?: return null
    val buffer = file.asDynamic().arrayBuffer().unsafeCast<Promise<org.khronos.webgl.ArrayBuffer>>().await()
    return PickedFile(file.name, Int8Array(buffer).unsafeCast<ByteArray>())
}

/** Hands [bytes] to the browser as a download called [name]. */
internal fun downloadFile(name: String, bytes: ByteArray, type: String) {
    val url = URL.createObjectURL(Blob(arrayOf(bytes), BlobPropertyBag(type = type)))
    val link = document.createElement("a") as HTMLAnchorElement
    link.href = url
    link.download = name
    link.click()
    window.setTimeout({ URL.revokeObjectURL(url) }, 60_000)
}

/** The local test-photo server's backbones, on a developer's machine (see docs/user-trained-models.md). */
internal class WebLocalBackbones : LocalBackbones {
    override suspend fun locations(): Map<String, String> =
        BACKBONES.associateWith { "$LOCAL_SERVER/backbones/$it.tflite" }.filterValues { url ->
            runCatching { fetch(url, kotlin.js.json("method" to "HEAD")).ok }.getOrDefault(false)
        }

    override suspend fun read(location: String): ByteArray {
        val response = fetch(location)
        check(response.ok) { "HTTP ${response.status} for $location" }
        return Int8Array(response.arrayBuffer().await()).unsafeCast<ByteArray>()
    }
}

// A plain object for the options: Kotlin's RequestInit would send nulls the browser rejects
private suspend fun fetch(url: String, init: dynamic = js("({})")): Response =
    window.asDynamic().fetch(url, init).unsafeCast<Promise<Response>>().await()

/** A folder chooser: every file inside comes with its path, e.g. "weeds/Lantana camara/001.jpg". */
private suspend fun pickFolder(): Dataset? {
    val result = CompletableDeferred<Dataset?>()
    chooseFiles({ asDynamic().webkitdirectory = true }) { list ->
        val paths = list.map { (it.asDynamic().webkitRelativePath as String) to WebLocalImage(it) }
        val name = paths.firstOrNull()?.first?.substringBefore('/')
        result.complete(name?.let { Dataset.fromPaths(it, paths) })
    }
    return result.await()
}

/**
 * Files in the browser's private storage for this site (the Origin Private File System), which
 * survives closing the tab. Where a browser doesn't offer it, files only last until the page closes.
 */
internal class WebAppFiles : AppFiles {
    private val memory = mutableMapOf<String, ByteArray>()

    // Typed Any? rather than dynamic: a suspend function returning dynamic breaks Kotlin/JS coroutines
    private suspend fun root(): Any? {
        val storage = window.navigator.asDynamic().storage
        if (storage == null || storage.getDirectory == null) return null
        return runCatching { storage.getDirectory().unsafeCast<Promise<Any?>>().await() }.getOrNull()
    }

    /** The folder holding [name], created if [create]; null if it isn't there. */
    private suspend fun folder(name: String, create: Boolean): Any? {
        var dir: Any = root() ?: return null
        val options: dynamic = js("({})")
        options.create = create
        for (part in name.split('/').dropLast(1)) {
            dir = runCatching { dir.asDynamic().getDirectoryHandle(part, options).unsafeCast<Promise<Any?>>().await() }.getOrNull() ?: return null
        }
        return dir
    }

    private suspend fun file(name: String): Blob? {
        val dir = folder(name, create = false) ?: return null
        val handle = runCatching { dir.asDynamic().getFileHandle(name.substringAfterLast('/')).unsafeCast<Promise<Any?>>().await() }.getOrNull()
            ?: return null
        return handle.asDynamic().getFile().unsafeCast<Promise<Blob>>().await()
    }

    override suspend fun read(name: String): ByteArray? {
        if (root() == null) return memory[name]
        val blob = file(name) ?: return null
        return Int8Array(blob.asDynamic().arrayBuffer().unsafeCast<Promise<org.khronos.webgl.ArrayBuffer>>().await()).unsafeCast<ByteArray>()
    }

    override suspend fun write(name: String, bytes: ByteArray) {
        if (root() == null) { memory[name] = bytes; return }
        val dir = folder(name, create = true) ?: error("No storage")
        val handle = dir.asDynamic().getFileHandle(name.substringAfterLast('/'), js("({ create: true })")).unsafeCast<Promise<Any>>().await().asDynamic()
        // A writable stream only replaces the file when closed: a stop part-way keeps the old one
        val stream = handle.createWritable().unsafeCast<Promise<Any>>().await().asDynamic()
        stream.write(bytes).unsafeCast<Promise<Any?>>().await()
        stream.close().unsafeCast<Promise<Any?>>().await()
    }

    override suspend fun append(name: String, text: String) {
        if (root() == null) { memory[name] = (memory[name] ?: ByteArray(0)) + text.encodeToByteArray(); return }
        val dir = folder(name, create = true) ?: error("No storage")
        val handle = dir.asDynamic().getFileHandle(name.substringAfterLast('/'), js("({ create: true })")).unsafeCast<Promise<Any>>().await().asDynamic()
        val stream = handle.createWritable(js("({ keepExistingData: true })")).unsafeCast<Promise<Any>>().await().asDynamic()
        val size: Any? = handle.getFile().unsafeCast<Promise<Any>>().await().asDynamic().size
        stream.seek(size).unsafeCast<Promise<Any?>>().await()
        stream.write(text).unsafeCast<Promise<Any?>>().await()
        stream.close().unsafeCast<Promise<Any?>>().await()
    }

    override suspend fun delete(name: String) {
        if (root() == null) { memory.keys.removeAll { it == name || it.startsWith("$name/") }; return }
        val dir = folder(name, create = false) ?: return
        runCatching { dir.asDynamic().removeEntry(name.substringAfterLast('/'), js("({ recursive: true })")).unsafeCast<Promise<Any?>>().await() }
    }

    override suspend fun list(folder: String): List<String> {
        if (root() == null) return memory.keys.filter { it.startsWith("$folder/") }.map { it.removePrefix("$folder/").substringBefore('/') }.distinct().sorted()
        val dir = folder("$folder/x", create = false) ?: return emptyList()
        val names = mutableListOf<String>()
        val entries = dir.asDynamic().keys()
        while (true) {
            val next = entries.next().unsafeCast<Promise<Any>>().await().asDynamic()
            if (next.done == true) break
            names += next.value as String
        }
        return names.sorted()
    }

    override suspend fun location(name: String): String {
        val blob: Blob = if (root() == null) Blob(arrayOf(memory.getValue(name))) else file(name) ?: error("No $name")
        return URL.createObjectURL(blob)
    }
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
