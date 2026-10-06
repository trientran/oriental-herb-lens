package com.uri.lee.dl.shared

import com.uri.lee.dl.shared.research.ResearchBackground
import com.uri.lee.dl.shared.research.ResearchFiles
import co.touchlab.kermit.Logger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.BackgroundTasks.BGContinuedProcessingTask
import platform.BackgroundTasks.BGContinuedProcessingTaskRequest
import platform.BackgroundTasks.BGContinuedProcessingTaskRequestSubmissionStrategy
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.closeFile
import platform.Foundation.dataUsingEncoding
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.seekToEndOfFile
import platform.Foundation.writeData
import platform.Foundation.writeToFile
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

/**
 * Keeps a research run going with the screen locked or another app open, on iOS 26 and later: a
 * continued processing task, which iOS shows on the lock screen with its progress and the user
 * can cancel. Older iOS suspends the app soon after it leaves the screen; the run then resumes
 * from its saved progress.
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosResearchBackground : ResearchBackground {
    private const val PREFIX = "com.uri.lee.dl.research"

    private var task: BGContinuedProcessingTask? = null
    private var onExpired: (() -> Unit)? = null

    val supported: Boolean get() = NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(
        kotlinx.cinterop.cValue { majorVersion = 26; minorVersion = 0; patchVersion = 0 },
    )

    override fun start(title: String, onExpired: () -> Unit) {
        if (!supported) return
        this.onExpired = onExpired
        // UIKit-side work on the main thread
        dispatch_async(dispatch_get_main_queue()) {
            // Info.plist permits PREFIX.*, but each task needs a handler under its own identifier,
            // registered just before it's submitted (Apple DTS: a wildcard handler isn't matched).
            // Submitting without one throws, which would end the app: hence the check.
            val identifier = "$PREFIX.${NSUUID().UUIDString}"
            val registered = BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(identifier, usingQueue = null) { started ->
                val continued = started as? BGContinuedProcessingTask ?: return@registerForTaskWithIdentifier
                task = continued
                continued.expirationHandler = {
                    this.onExpired?.invoke()
                    continued.setTaskCompletedWithSuccess(false)
                    task = null
                }
            }
            if (!registered) {
                Logger.withTag("Research").w { "Couldn't register the background task" }
                return@dispatch_async
            }
            val request = BGContinuedProcessingTaskRequest(identifier, title, "Starting…")
            // Run now or not at all: a run started on the screen shouldn't wait in a queue
            request.strategy = BGContinuedProcessingTaskRequestSubmissionStrategy.BGContinuedProcessingTaskRequestSubmissionStrategyFail
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                if (!BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error.ptr)) {
                    Logger.withTag("Research").w { "No background time: ${error.value?.localizedDescription}" }
                }
            }
        }
    }

    override fun progress(done: Int, total: Int, text: String) {
        val current = task ?: return
        current.progress.totalUnitCount = total.toLong()
        current.progress.completedUnitCount = done.toLong()
        current.updateTitle(current.title, text)
    }

    override fun stop() {
        onExpired = null
        task?.setTaskCompletedWithSuccess(true)
        task = null
    }
}

/** Files in Library/Application Support/research: kept, but not shown in Files or Finder. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosResearchFiles : ResearchFiles {
    private val root: String by lazy {
        val support = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
        "$support/research"
    }

    private fun path(name: String) = "$root/$name"

    private fun ensureFolder(path: String) {
        NSFileManager.defaultManager.createDirectoryAtPath(path.substringBeforeLast('/'), true, null, null)
    }

    override suspend fun read(name: String): ByteArray? = withContext(Dispatchers.IO) {
        val data = NSData.dataWithContentsOfFile(path(name)) ?: return@withContext null
        ByteArray(data.length.toInt()).apply { if (isNotEmpty()) usePinned { memcpy(it.addressOf(0), data.bytes, data.length) } }
    }

    override suspend fun write(name: String, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            val target = path(name)
            ensureFolder(target)
            val data = if (bytes.isEmpty()) NSData() else bytes.usePinned { NSData.dataWithBytes(it.addressOf(0), bytes.size.convert()) }
            // Atomic: written aside, then moved over the old file
            check(data.writeToFile(target, atomically = true)) { "Couldn't save $name" }
        }
    }

    override suspend fun append(name: String, text: String) {
        withContext(Dispatchers.IO) {
            val target = path(name)
            ensureFolder(target)
            if (!NSFileManager.defaultManager.fileExistsAtPath(target)) NSFileManager.defaultManager.createFileAtPath(target, null, null)
            val handle = NSFileHandle.fileHandleForWritingAtPath(target) ?: error("Couldn't open $name")
            handle.seekToEndOfFile()
            @Suppress("CAST_NEVER_SUCCEEDS")
            handle.writeData((text as platform.Foundation.NSString).dataUsingEncoding(platform.Foundation.NSUTF8StringEncoding)!!)
            handle.closeFile()
        }
    }

    override suspend fun delete(name: String) {
        withContext(Dispatchers.IO) { NSFileManager.defaultManager.removeItemAtPath(path(name), null) }
    }

    override suspend fun location(name: String): String = NSURL.fileURLWithPath(path(name)).path ?: path(name)
}
