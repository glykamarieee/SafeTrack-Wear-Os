package com.safetrack.watch.services.sos

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.safetrack.watch.SafeTrackApp
import java.util.concurrent.TimeUnit

/**
 * Runs when the network is available, even if the monitoring service was
 * stopped: sends pending SOS first, then a location kept offline, then a heartbeat.
 * The one-time job retries with backoff until every pending SOS is sent.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SafeTrackApp).container
        if (container.devices.session.value == null) return Result.success()

        val allSosSent = container.sos.transmitAll()
        container.monitoring.flushPendingLocation()
        container.monitoring.heartbeat()

        return if (!allSosSent && tags.contains(TAG_SOS_RETRY)) Result.retry() else Result.success()
    }

    companion object {
        private const val TAG_SOS_RETRY = "sos-retry"
        private const val SOS_RETRY_WORK = "safetrack-sos-retry"
        private const val PERIODIC_WORK = "safetrack-periodic-sync"

        private val online = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Retries pending SOS as soon as the network returns. */
        fun scheduleSosRetry(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(TAG_SOS_RETRY)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(SOS_RETRY_WORK, ExistingWorkPolicy.KEEP, request)
        }

        /** Backup heartbeat for when the monitoring service is not running. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(online)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
        }
    }
}
