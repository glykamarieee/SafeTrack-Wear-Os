package com.safetrack.watch.domain.repository

import com.safetrack.watch.domain.model.MonitoringSnapshot
import com.safetrack.watch.domain.model.Outcome
import com.safetrack.watch.domain.model.PendingSos
import com.safetrack.watch.domain.model.Session
import com.safetrack.watch.domain.model.WatchLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DeviceRepository {
    /** Null until the watch is paired; cleared if SafeTrack revokes the watch. */
    val session: StateFlow<Session?>

    /** Why the session ended, if SafeTrack revoked it. Shown once on the connection screen. */
    val unlinkNotice: StateFlow<String?>

    /** The child's name right after a successful pairing, until the child taps Continue. */
    val justConnected: StateFlow<String?>

    fun acknowledgeConnected()

    /** Stable ST-WATCH-XXXXXXXX identifier for this watch. */
    suspend fun watchId(): String

    suspend fun pair(connectionCode: String): Outcome<Session>

    fun clearUnlinkNotice()
}

interface MonitoringRepository {
    val snapshot: StateFlow<MonitoringSnapshot>

    /** Sends a heartbeat unless SafeTrack was reached very recently. Returns true when SafeTrack is reachable. */
    suspend fun heartbeat(force: Boolean = false): Boolean

    /** Sends a location, or keeps it to send later if SafeTrack cannot be reached. */
    suspend fun reportLocation(location: WatchLocation)

    /** Sends a location kept while offline, if any. */
    suspend fun flushPendingLocation()

    fun setLocationAvailable(available: Boolean)

    /** Re-reads battery and network. */
    fun refreshDeviceStatus()
}

interface SosRepository {
    val pending: Flow<List<PendingSos>>

    /** Persists a confirmed SOS. Must be called before the first send attempt. */
    suspend fun save(sos: PendingSos)

    /** Sends one SOS. True once SafeTrack has recorded it (including an earlier duplicate). */
    suspend fun transmit(localId: String): Boolean

    /** Sends every pending SOS, oldest first. True when none remain. */
    suspend fun transmitAll(): Boolean

    /** SafeTrack's id for an SOS this watch delivered since the app started, if known. */
    fun deliveredAlertId(localId: String): String?

    /** Removes an SOS that hasn't been sent yet, so it is never sent. */
    suspend fun discard(localId: String)

    /** Cancels a delivered SOS sent by mistake (the guardian is told). True when it is no longer active. */
    suspend fun cancelDelivered(alertId: String): Boolean
}
