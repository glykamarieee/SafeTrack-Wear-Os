package com.safetrack.watch.data.repository

import com.safetrack.watch.data.local.DeviceStatusReader
import com.safetrack.watch.data.local.LocalStore
import com.safetrack.watch.domain.model.ConnectionState
import com.safetrack.watch.domain.model.DeviceStatus
import com.safetrack.watch.domain.model.MonitoringSnapshot
import com.safetrack.watch.domain.model.WatchLocation
import com.safetrack.watch.domain.repository.MonitoringRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put

class MonitoringRepositoryImpl(
    private val link: WatchLink,
    private val store: LocalStore,
    private val status: DeviceStatusReader,
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : MonitoringRepository {

    private data class LocalState(
        val lastLocationAtMillis: Long? = null,
        val locationAvailable: Boolean = true,
        val device: DeviceStatus,
        /** Bumped to re-evaluate staleness as time passes. */
        val tick: Long = 0,
    )

    private val local = MutableStateFlow(LocalState(device = status.read()))
    private val locationMutex = Mutex()

    override val snapshot: StateFlow<MonitoringSnapshot> =
        combine(link.state, local, store.pendingSos) { linkState, localState, pendingSos ->
            MonitoringSnapshot(
                connection = connectionFrom(linkState),
                lastSyncAtMillis = linkState.lastSuccessAtMillis,
                safeZone = linkState.safeZone,
                lastLocationAtMillis = localState.lastLocationAtMillis,
                locationAvailable = localState.locationAvailable,
                device = localState.device,
                pendingSosCount = pendingSos.size,
            )
        }.stateIn(scope, SharingStarted.Eagerly, MonitoringSnapshot())

    init {
        scope.launch {
            link.restore()
            val saved = store.syncState()
            local.value = local.value.copy(lastLocationAtMillis = saved.lastLocationAtMillis)
        }
    }

    private fun connectionFrom(state: WatchLink.State): ConnectionState {
        val lastOk = state.lastSuccessAtMillis
        val fresh = lastOk != null && clock() - lastOk <= STALE_AFTER_MS
        return when {
            fresh && !state.lastAttemptFailed -> ConnectionState.CONNECTED
            state.inFlight > 0 -> ConnectionState.CONNECTING
            lastOk == null && !state.lastAttemptFailed -> ConnectionState.CONNECTING
            else -> ConnectionState.DISCONNECTED
        }
    }

    override suspend fun heartbeat(force: Boolean): Boolean {
        val lastOk = link.state.value.lastSuccessAtMillis
        if (!force && lastOk != null && clock() - lastOk < HEARTBEAT_SKIP_MS && !link.state.value.lastAttemptFailed) {
            return true
        }
        refreshDeviceStatus()
        return link.send("record_watch_heartbeat") is WatchLink.Result.Delivered
    }

    override suspend fun reportLocation(location: WatchLocation) {
        store.saveLastLocationAt(location.recordedAtMillis)
        local.value = local.value.copy(lastLocationAtMillis = location.recordedAtMillis, locationAvailable = true)
        refreshDeviceStatus()

        locationMutex.withLock {
            when (val result = link.send("record_watch_location") { putLocation(location) }) {
                is WatchLink.Result.Delivered -> store.setPendingLocation(null)
                // Keep only the newest fix; older offline fixes are superseded.
                is WatchLink.Result.Failed -> if (result.retryable) store.setPendingLocation(location)
            }
        }
    }

    override suspend fun flushPendingLocation() {
        locationMutex.withLock {
            val pending = store.pendingLocation() ?: return
            when (val result = link.send("record_watch_location") { putLocation(pending) }) {
                is WatchLink.Result.Delivered -> store.setPendingLocation(null)
                is WatchLink.Result.Failed -> if (!result.retryable) store.setPendingLocation(null)
            }
        }
    }

    override fun setLocationAvailable(available: Boolean) {
        local.value = local.value.copy(locationAvailable = available)
    }

    override fun refreshDeviceStatus() {
        local.value = local.value.copy(device = status.read(), tick = local.value.tick + 1)
    }

    companion object {
        /** Matches the backend's default watch-disconnect window (WATCH_DISCONNECT_SECONDS = 300). */
        const val STALE_AFTER_MS = 5 * 60_000L

        /** A heartbeat is unnecessary if SafeTrack was reached this recently. */
        const val HEARTBEAT_SKIP_MS = 90_000L
    }
}

internal fun JsonObjectBuilder.putLocation(location: WatchLocation) {
    put("p_latitude", location.latitude)
    put("p_longitude", location.longitude)
    location.accuracyMeters?.let { put("p_accuracy_meters", it) }
    put("p_recorded_at", Iso.format(location.recordedAtMillis))
}
