package com.safetrack.watch.data.repository

import android.util.Log
import com.safetrack.watch.data.local.LocalStore
import com.safetrack.watch.data.remote.string
import com.safetrack.watch.domain.model.PendingSos
import com.safetrack.watch.domain.model.WatchLocation
import com.safetrack.watch.domain.repository.SosRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.put

/**
 * Confirmed SOS records are written to disk first and only removed once
 * SafeTrack has recorded them. The server treats a second send with the same
 * triggeredAt as the same alert, so a retry after a lost response does not
 * create a duplicate.
 */
class SosRepositoryImpl(
    private val link: WatchLink,
    private val store: LocalStore,
    private val devices: DeviceRepositoryImpl,
    private val clock: () -> Long = System::currentTimeMillis,
) : SosRepository {

    /** One SOS send at a time, so two retry paths never race each other. */
    private val mutex = Mutex()

    override val pending: Flow<List<PendingSos>> = store.pendingSos

    override suspend fun save(sos: PendingSos) {
        store.updatePendingSos { list -> list.filterNot { it.localId == sos.localId } + sos }
    }

    override suspend fun transmit(localId: String): Boolean = mutex.withLock {
        val sos = store.pendingSos.first().firstOrNull { it.localId == localId } ?: return true
        val session = devices.session.value ?: return false

        if (sos.childId != session.childId) {
            // The watch was re-paired to a different child; this alert belongs to the old link.
            Log.w(TAG, "Dropping SOS ${sos.localId} created for a previous child link")
            remove(localId)
            return true
        }

        store.updatePendingSos { list ->
            list.map {
                if (it.localId == localId) it.copy(attempts = it.attempts + 1, lastAttemptAtMillis = clock()) else it
            }
        }

        // Latest available location: the fix taken at confirmation, else the newest fix kept offline.
        val location = if (sos.sendWithoutLocation) null else sos.location ?: store.pendingLocation()

        var result = send(sos, location)

        if (result is WatchLink.Result.Failed && !result.retryable && location != null) {
            // SafeTrack refused the content (e.g. invalid_location). An SOS without a
            // location is better than none, so resend at once without it.
            Log.w(TAG, "SOS ${sos.localId} refused with its location; resending without it")
            store.updatePendingSos { list ->
                list.map { if (it.localId == localId) it.copy(sendWithoutLocation = true) else it }
            }
            result = send(sos, null)
        }

        when (result) {
            is WatchLink.Result.Delivered -> {
                Log.i(TAG, "SOS ${sos.localId} recorded as ${result.response.string("sosAlertId")}")
                remove(localId)
                if (location != null && sos.location == null) store.setPendingLocation(null)
                true
            }
            is WatchLink.Result.Failed -> {
                if (result.retryable) {
                    Log.w(TAG, "SOS ${sos.localId} not sent yet (attempt ${sos.attempts + 1})")
                    false
                } else {
                    // Resending identical content cannot succeed, and transmitAll stops at the
                    // first failure, so keeping it would block every later SOS.
                    Log.e(TAG, "SOS ${sos.localId} refused by SafeTrack; dropping it")
                    remove(localId)
                    true
                }
            }
        }
    }

    private suspend fun send(sos: PendingSos, location: WatchLocation?): WatchLink.Result =
        link.send("trigger_watch_sos") {
            put("p_activation_method", sos.activation)
            put("p_triggered_at", sos.triggeredAt)
            location?.let { putLocation(it) }
        }

    override suspend fun transmitAll(): Boolean {
        for (sos in store.pendingSos.first().sortedBy { it.triggeredAt }) {
            if (!transmit(sos.localId)) return false
        }
        return store.pendingSos.first().isEmpty()
    }

    private suspend fun remove(localId: String) {
        store.updatePendingSos { list -> list.filterNot { it.localId == localId } }
    }

    private companion object {
        const val TAG = "SosRepository"
    }
}
