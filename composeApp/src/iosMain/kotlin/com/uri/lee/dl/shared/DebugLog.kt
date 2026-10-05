package com.uri.lee.dl.shared

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.posix.FILE
import platform.posix.fflush
import platform.posix.fopen
import platform.posix.fputs

/**
 * Debug builds also write the log to Documents/debug/app.log, which can be copied off a device
 * without Xcode attached:
 *
 *     xcrun devicectl device copy from --device <id> --domain-type appDataContainer \
 *         --domain-identifier com.uri.lee.dl --source Documents/debug --destination ./debug
 *
 * iOS sends the usual log to the unified system log, which needs Xcode or Console to read.
 */
@OptIn(ExperimentalForeignApi::class)
internal object DebugLog {
    val directory: String by lazy {
        val documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
        "$documents/debug".also { NSFileManager.defaultManager.createDirectoryAtPath(it, true, null, null) }
    }

    fun start() {
        val file = fopen("$directory/app.log", "a") ?: return
        Logger.addLogWriter(FileLogWriter(file))
        Logger.withTag("App").i { "Started" }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class FileLogWriter(private val file: CPointer<FILE>) : LogWriter() {
    private val time = NSDateFormatter().apply { dateFormat = "HH:mm:ss.SSS" }

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val error = throwable?.let { "\n" + it.stackTraceToString() }.orEmpty()
        fputs("${time.stringFromDate(NSDate())} ${severity.name.first()}/$tag: $message$error\n", file)
        fflush(file)
    }
}

/** Lets Swift write to the same log, e.g. what App Check or a sign-in bridge reported. */
fun debugLog(tag: String, message: String) = Logger.withTag(tag).i { message }
