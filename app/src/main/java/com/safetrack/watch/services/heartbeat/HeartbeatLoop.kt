package com.safetrack.watch.services.heartbeat

import android.content.Context
import com.safetrack.watch.domain.repository.MonitoringRepository
import com.safetrack.watch.domain.repository.SosRepository
import com.safetrack.watch.services.withWakeLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Keeps SafeTrack's last_seen_at fresh so the guardian can tell the watch is
 * active. Each round sends pending SOS first (highest priority), then any
 * location kept offline, then the heartbeat itself (skipped if a location
 * upload reached SafeTrack moments ago).
 */
class HeartbeatLoop(
    private val context: Context,
    private val monitoring: MonitoringRepository,
    private val sos: SosRepository,
) {
    private var job: Job? = null
    private val roundLock = Mutex()

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                syncNow(force = false)
                delay(INTERVAL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** One round; concurrent requests (e.g. network regained during a round) are dropped. */
    suspend fun syncNow(force: Boolean) {
        if (!roundLock.tryLock()) return
        try {
            withWakeLock(context, "heartbeat") {
                sos.transmitAll()
                monitoring.flushPendingLocation()
                monitoring.heartbeat(force)
            }
        } finally {
            roundLock.unlock()
        }
    }

    companion object {
        /** Well inside the backend's 5-minute disconnect window. */
        const val INTERVAL_MS = 2 * 60_000L
    }
}
