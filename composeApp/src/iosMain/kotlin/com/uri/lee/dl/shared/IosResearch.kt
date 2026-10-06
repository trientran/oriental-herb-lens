package com.uri.lee.dl.shared

import com.uri.lee.dl.core.ml.IosPickedImage
import com.uri.lee.dl.core.training.ResourceMonitor
import com.uri.lee.dl.core.training.ResourceSample
import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.domain.training.AppFiles
import com.uri.lee.dl.domain.training.Dataset
import com.uri.lee.dl.domain.training.scoped
import com.uri.lee.dl.feature.training.PickedFile
import com.uri.lee.dl.shared.training.LocalBackbones
import org.koin.mp.KoinPlatform
import com.uri.lee.dl.shared.research.ResearchPlatform
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSProcessInfoThermalState
import platform.Foundation.NSSearchPathForDirectoriesInDomains

import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.thermalState
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryState
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.darwin.NSObject
import platform.darwin.TASK_VM_INFO
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.mach_msg_type_number_tVar
import platform.darwin.mach_task_self_
import platform.darwin.task_info
import platform.darwin.task_vm_info_data_t
import platform.posix.RUSAGE_SELF
import platform.posix.getrusage
import platform.posix.memcpy
import platform.posix.rusage
import platform.posix.uname
import platform.posix.utsname

/**
 * iOS's side of research mode (plan Phase 7): backbones copied into Documents/backbones (see
 * docs/user-trained-models.md), datasets from a folder chosen in Files (Files unpacks a zip with
 * a tap), results through the share sheet (Save to Files, AirDrop).
 */
internal class IosResearch(private val topController: () -> UIViewController) {
    // UIKit holds delegates weakly; this keeps the picker's alive while it's open
    private var folderDelegate: FolderPickerDelegate? = null

    @OptIn(ExperimentalForeignApi::class)
    fun platform() = ResearchPlatform(
        device = deviceName(),
        platform = "ios",
        monitor = IosResourceMonitor(),
        files = KoinPlatform.getKoin().get<AppFiles>().scoped("research"),
        pickDatasetFolder = ::pickFolder,
        appDatasets = {
            withContext(Dispatchers.IO) {
                val folder = documents() + "/datasets"
                NSFileManager.defaultManager.contentsOfDirectoryAtPath(folder, error = null).orEmpty()
                    .filterIsInstance<String>().filterNot { it.startsWith('.') }.sorted()
                    .mapNotNull { name -> readFolder("$folder/$name", name) }
            }
        },
        // Kept in Documents/research too, where devicectl or Finder can copy it off the phone
        saveArchive = { name, bytes -> share(name, bytes, "research") },
        background = IosResearchBackground,
        backgroundNote = if (IosResearchBackground.supported) {
            "The run carries on with the screen locked or in another app; iOS shows its progress on the lock screen and may end it " +
                "if the phone is busy. If it stops, open Research mode again and tap Resume: finished runs are kept."
        } else {
            "Keep Herb Lens open: iOS before 26 pauses it when it leaves the screen. If it stops, open Research mode again and tap Resume."
        },
        keepAwake = { on -> dispatch_async(dispatch_get_main_queue()) { UIApplication.sharedApplication.idleTimerDisabled = on } },
    )

    /** Files for user-trained models: datasets, model files, sharing a model. */
    fun fileActions() = FileActions(
        pickDatasetFolder = ::pickFolder,
        pickModelFile = ::pickModelFile,
        saveFile = { name, bytes -> share(name, bytes, "models") },
    )

    /** Writes [bytes] to Documents/[folder]/[name] and offers it in the share sheet (Save to Files, AirDrop). */
    @OptIn(ExperimentalForeignApi::class)
    private suspend fun share(name: String, bytes: ByteArray, folder: String) {
        val directory = documents() + "/" + folder
        val path = "$directory/$name"
        withContext(Dispatchers.IO) {
            NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
            bytes.usePinned { NSData.dataWithBytes(it.addressOf(0), bytes.size.convert()) }.writeToFile(path, atomically = true)
        }
        val sheet = UIActivityViewController(listOf(NSURL.fileURLWithPath(path)), applicationActivities = null)
        val host = topController()
        // iPad shows the sheet as a popover, which needs an anchor
        sheet.popoverPresentationController?.sourceView = host.view
        host.presentViewController(sheet, animated = true, completion = null)
    }

    /** A file chosen in Files (copied into the app first), e.g. a .tflite model. */
    @OptIn(ExperimentalForeignApi::class)
    private suspend fun pickModelFile(): PickedFile? {
        val result = CompletableDeferred<NSURL?>()
        val delegate = FolderPickerDelegate { result.complete(it) }
        folderDelegate = delegate
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData), asCopy = true)
        picker.delegate = delegate
        topController().presentViewController(picker, animated = true, completion = null)
        val url = result.await()
        folderDelegate = null
        val path = url?.path ?: return null
        return withContext(Dispatchers.IO) {
            val data = NSData.dataWithContentsOfFile(path) ?: return@withContext null
            val bytes = ByteArray(data.length.toInt()).apply { if (isNotEmpty()) usePinned { memcpy(it.addressOf(0), data.bytes, data.length) } }
            PickedFile(url.lastPathComponent ?: "model.tflite", bytes)
        }
    }

    private suspend fun pickFolder(): Dataset? {
        val result = CompletableDeferred<NSURL?>()
        val delegate = FolderPickerDelegate { result.complete(it) }
        folderDelegate = delegate
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder))
        picker.delegate = delegate
        topController().presentViewController(picker, animated = true, completion = null)
        val folder = result.await()
        folderDelegate = null
        folder ?: return null
        // Kept open for the rest of the session: the photos are read during the run
        folder.startAccessingSecurityScopedResource()
        return withContext(Dispatchers.IO) { readFolder(folder.path ?: return@withContext null, folder.lastPathComponent ?: "dataset") }
    }

    /** Every file under [root], by its path inside it. */
    private fun readFolder(root: String, name: String): Dataset? {
        val enumerator = NSFileManager.defaultManager.enumeratorAtPath(root) ?: return null
        val files = mutableListOf<Pair<String, LocalImage>>()
        while (true) {
            val relative = enumerator.nextObject() as? String ?: break
            files += relative to IosPickedImage(NSURL.fileURLWithPath("$root/$relative").absoluteString ?: continue)
        }
        return Dataset.fromPaths(name, files)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun deviceName(): String {
        val model = memScoped {
            val info = alloc<utsname>()
            uname(info.ptr)
            info.machine.toKString() // e.g. "iPhone18,2"
        }
        val device = UIDevice.currentDevice
        return "$model (${device.systemName} ${device.systemVersion})"
    }

    private fun documents() = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
}

private class FolderPickerDelegate(private val onResult: (NSURL?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        onResult(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = onResult(null)
}

/**
 * The app's memory footprint (what iOS counts against its limit), CPU time, thermal state and
 * battery. Battery readings come from UIKit on the main thread, so from elsewhere the last ones
 * are reported and a fresh reading is requested.
 */
@OptIn(ExperimentalForeignApi::class)
private class IosResourceMonitor : ResourceMonitor {
    private var batteryPercent: Int? = null
    private var charging: Boolean? = null
    private var foreground: Boolean? = null

    init {
        dispatch_async(dispatch_get_main_queue()) {
            UIDevice.currentDevice.batteryMonitoringEnabled = true
            readBattery()
        }
    }

    private fun readBattery() {
        foreground = UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive
        val device = UIDevice.currentDevice
        batteryPercent = device.batteryLevel.takeIf { it >= 0f }?.let { (it * 100).toInt() }
        charging = when (device.batteryState) {
            UIDeviceBatteryState.UIDeviceBatteryStateCharging, UIDeviceBatteryState.UIDeviceBatteryStateFull -> true
            UIDeviceBatteryState.UIDeviceBatteryStateUnplugged -> false
            else -> null
        }
    }

    override fun sample(): ResourceSample {
        if (NSThread.isMainThread) readBattery() else dispatch_async(dispatch_get_main_queue()) { readBattery() }
        return ResourceSample(
            memoryBytes = footprint(),
            cpuMillis = cpuMillis(),
            batteryPercent = batteryPercent,
            charging = charging,
            foreground = foreground,
            thermal = when (NSProcessInfo.processInfo.thermalState) {
                NSProcessInfoThermalState.NSProcessInfoThermalStateNominal -> "nominal"
                NSProcessInfoThermalState.NSProcessInfoThermalStateFair -> "fair"
                NSProcessInfoThermalState.NSProcessInfoThermalStateSerious -> "serious"
                NSProcessInfoThermalState.NSProcessInfoThermalStateCritical -> "critical"
                else -> null
            },
        )
    }

    private fun footprint(): Long? = memScoped {
        val info = alloc<task_vm_info_data_t>()
        val count = alloc<mach_msg_type_number_tVar>()
        count.value = (sizeOf<task_vm_info_data_t>() / 4).convert()
        val status = task_info(mach_task_self_, TASK_VM_INFO.convert(), info.ptr.reinterpret(), count.ptr)
        if (status == 0) info.phys_footprint.toLong() else null
    }

    private fun cpuMillis(): Long? = memScoped {
        val usage = alloc<rusage>()
        if (getrusage(RUSAGE_SELF, usage.ptr) != 0) return@memScoped null
        usage.ru_utime.tv_sec * 1000 + usage.ru_utime.tv_usec / 1000 + usage.ru_stime.tv_sec * 1000 + usage.ru_stime.tv_usec / 1000
    }
}

/** Backbones copied into Documents/backbones with devicectl or Finder (developers; see docs/user-trained-models.md). */
@OptIn(ExperimentalForeignApi::class)
internal class IosLocalBackbones : LocalBackbones {
    override suspend fun locations(): Map<String, String> = withContext(Dispatchers.IO) {
        val documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
        val folder = "$documents/backbones"
        NSFileManager.defaultManager.contentsOfDirectoryAtPath(folder, error = null).orEmpty()
            .filterIsInstance<String>().filter { it.endsWith(".tflite") }
            .associate { it.removeSuffix(".tflite") to "$folder/$it" }
    }

    override suspend fun read(location: String): ByteArray = withContext(Dispatchers.IO) {
        val data = NSData.dataWithContentsOfFile(location) ?: error("Can't read $location")
        ByteArray(data.length.toInt()).apply { usePinned { memcpy(it.addressOf(0), data.bytes, data.length) } }
    }
}
