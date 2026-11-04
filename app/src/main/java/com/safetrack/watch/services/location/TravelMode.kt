package com.safetrack.watch.services.location

import com.google.android.gms.location.DetectedActivity

/**
 * How the child is moving: GPS speed, refined by Android Activity Recognition
 * when it is allowed and recent. Same speed bands as the phone app
 * (safe-track/lib/travelMode.ts). GPS cannot tell a car from a jeep or a
 * motorcycle, so any vehicle is "vehicle"; "boat" is decided by the guardian
 * app from the map (water). Values must match location_logs_travel_mode_check.
 */
object TravelMode {
    const val STILL = "still"
    const val WALKING = "walking"
    const val RUNNING = "running"
    const val BIKING = "biking"
    const val VEHICLE = "vehicle"
    const val UNKNOWN = "unknown"

    const val STILL_BELOW_MPS = 0.6f
    const val WALKING_BELOW_MPS = 2.2f
    const val RUNNING_BELOW_MPS = 3.5f
    const val BIKING_BELOW_MPS = 7f

    /** Activity Recognition older than this is ignored. */
    const val ACTIVITY_MAX_AGE_MS = 2 * 60_000L

    data class Estimate(val mode: String, val confidence: Float)

    fun fromSpeed(speedMps: Float?, accuracyMeters: Float?): Estimate {
        if (speedMps == null || speedMps.isNaN() || speedMps < 0f) return Estimate(UNKNOWN, 0f)
        val quality = when {
            accuracyMeters == null -> 0.8f
            accuracyMeters <= 20f -> 1f
            accuracyMeters <= 50f -> 0.7f
            else -> 0.4f
        }
        return when {
            speedMps < STILL_BELOW_MPS -> Estimate(STILL, 0.9f * quality)
            speedMps < WALKING_BELOW_MPS -> Estimate(WALKING, 0.8f * quality)
            speedMps < RUNNING_BELOW_MPS -> Estimate(RUNNING, 0.5f * quality)
            speedMps < BIKING_BELOW_MPS -> Estimate(BIKING, 0.5f * quality)
            else -> Estimate(VEHICLE, 0.8f * quality)
        }
    }

    /** Activity Recognition type -> mode; null for TILTING/UNKNOWN. */
    fun fromActivity(type: Int): String? = when (type) {
        DetectedActivity.IN_VEHICLE -> VEHICLE
        DetectedActivity.ON_BICYCLE -> BIKING
        DetectedActivity.RUNNING -> RUNNING
        DetectedActivity.WALKING, DetectedActivity.ON_FOOT -> WALKING
        DetectedActivity.STILL -> STILL
        else -> null
    }

    /**
     * Combines both. A confident, recent activity wins unless the speed clearly
     * disagrees (for example "still" while moving 10 m/s), then speed wins.
     */
    fun combine(speed: Estimate, activity: ActivityTracker.Detected?, nowMillis: Long, speedMps: Float?): Estimate {
        val recent = activity?.takeIf { nowMillis - it.atMillis <= ACTIVITY_MAX_AGE_MS && it.confidence >= 60 }
        val mode = recent?.let { fromActivity(it.type) } ?: return speed
        val confidence = recent.confidence / 100f
        if (speedMps == null) return Estimate(mode, confidence * 0.8f)
        val contradicts = (mode == STILL && speedMps > RUNNING_BELOW_MPS) ||
            ((mode == WALKING || mode == RUNNING) && speedMps > BIKING_BELOW_MPS * 1.5f)
        if (contradicts) return speed
        return Estimate(mode, if (mode == speed.mode) maxOf(confidence, speed.confidence) else confidence * 0.85f)
    }

    fun isMoving(mode: String) = mode != STILL && mode != UNKNOWN
}
