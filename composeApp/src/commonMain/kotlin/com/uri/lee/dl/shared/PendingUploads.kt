package com.uri.lee.dl.shared

import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.common.ApplicationScope
import com.uri.lee.dl.domain.upload.UploadQueue
import com.uri.lee.dl.domain.upload.UploadScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Every kind of saved upload (photos, shared models), tried in turn; one run at a time. */
class PendingUploads(private val queues: List<UploadQueue>) {
    private val lock = Mutex()

    /** Whether anything is left after [run], for another try later. */
    suspend fun hasPending(): Boolean = queues.any { runCatching { it.hasPending() }.getOrDefault(false) }

    suspend fun run() = lock.withLock {
        for (queue in queues) {
            try {
                queue.runPending()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.withTag("Uploads").w(e) { "Pending uploads failed; they stay for the next run" }
            }
        }
    }
}

/**
 * iOS and the web: runs saved uploads now, in the app's scope. If the app is closed first (iOS gives
 * it some background time), they run at the next launch ([StartupTasks] schedules them).
 */
class InProcessUploadScheduler(private val scope: ApplicationScope, private val uploads: PendingUploads) : UploadScheduler {
    override fun schedule() {
        scope.launch { uploads.run() }
    }
}
