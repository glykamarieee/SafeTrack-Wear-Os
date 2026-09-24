package com.safetrack.watch.domain.model

import kotlinx.serialization.Serializable

/** The watch's link to one child profile, obtained from watch-pair. */
data class Session(
    val watchId: String,
    val deviceToken: String,
    val deviceId: String,
    val childId: String,
    val childName: String,
)

/** Whether SafeTrack has heard from this watch recently (not whether the app is open). */
enum class ConnectionState { CONNECTED, CONNECTING, DISCONNECTED }

enum class NetworkType(val wireValue: String, val label: String) {
    WIFI("Wi-Fi", "Wi-Fi"),
    MOBILE("LTE", "LTE"),
    NONE("No network", "No network"),
    UNKNOWN("Unknown", "Unknown"),
}

data class DeviceStatus(
    val batteryPercent: Int?,
    val isCharging: Boolean,
    val network: NetworkType,
)

sealed interface SafeZoneStatus {
    data class Inside(val zoneName: String) : SafeZoneStatus
    data object Outside : SafeZoneStatus
    data class Unavailable(val reason: Reason) : SafeZoneStatus

    enum class Reason { NO_LOCATION, NO_SAFE_ZONE, NOT_CHECKED }

    companion object {
        /**
         * From the `safeZone` object the watch functions return, produced by the
         * database function safe_zone_status(): status is inside, outside,
         * no_safe_zone or unavailable; zoneName is set when inside.
         */
        fun fromServer(status: String?, zoneName: String?): SafeZoneStatus? = when (status) {
            "inside" -> Inside(zoneName?.trim().takeUnless { it.isNullOrEmpty() } ?: "Safe zone")
            "outside" -> Outside
            "no_safe_zone" -> Unavailable(Reason.NO_SAFE_ZONE)
            "unavailable" -> Unavailable(Reason.NO_LOCATION)
            else -> null
        }
    }
}

/** A location fix from the watch's own GNSS/fused provider. */
@Serializable
data class WatchLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val recordedAtMillis: Long,
    val source: String = "smartwatch",
)

/** Values must match sos_alerts_activation_method_check. */
enum class SosActivation(val wireValue: String) {
    TAP_AND_HOLD("tap_and_hold"),
    SHAKE("shake"),
}

/**
 * A confirmed SOS, persisted before the first send attempt so it survives
 * network loss, app restarts and reboots until SafeTrack accepts it.
 */
@Serializable
data class PendingSos(
    val localId: String,
    val childId: String,
    val activation: String,
    /** ISO-8601 time the child confirmed the SOS. Also the server's duplicate key. */
    val triggeredAt: String,
    val location: WatchLocation?,
    val attempts: Int = 0,
    val lastAttemptAtMillis: Long? = null,
)

/** What the SOS screen shows. */
sealed interface SosState {
    data object Idle : SosState
    data class Countdown(val activation: SosActivation, val secondsLeft: Int) : SosState
    data object Cancelled : SosState
    data object Sending : SosState
    data class Sent(val locationIncluded: Boolean) : SosState
    data class Pending(val locationIncluded: Boolean) : SosState
}

/** Everything the dashboard needs, kept in one place for the UI and services. */
data class MonitoringSnapshot(
    val connection: ConnectionState = ConnectionState.CONNECTING,
    val lastSyncAtMillis: Long? = null,
    val safeZone: SafeZoneStatus = SafeZoneStatus.Unavailable(SafeZoneStatus.Reason.NOT_CHECKED),
    val lastLocationAtMillis: Long? = null,
    val locationAvailable: Boolean = true,
    val device: DeviceStatus = DeviceStatus(null, false, NetworkType.UNKNOWN),
    val pendingSosCount: Int = 0,
)
