package com.uri.lee.dl.data.content

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.uri.lee.dl.domain.model.InstallResult
import com.uri.lee.dl.domain.usecase.SyncContentUseCase
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.util.concurrent.TimeUnit

/** Runs [SyncContentUseCase] with network access, retrying with backoff after network failures. */
class ContentSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val syncContent: SyncContentUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val results = syncContent()
        if (results.isNotEmpty()) Timber.i("Content sync: $results")
        if (results.values.any { it is InstallResult.Failed && it.retryable }) Result.retry() else Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Remote Config fetch failures land here; try again later.
        Timber.w(e, "Content sync failed")
        Result.retry()
    }

    companion object {
        private const val UNIQUE_NAME = "content-sync"

        /** Queues a sync; a sync that is already queued or running is kept rather than duplicated. */
        fun enqueue(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<ContentSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
