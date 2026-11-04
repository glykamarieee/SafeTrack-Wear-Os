package com.safetrack.watch.services.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.safetrack.watch.domain.model.WatchLocation
import com.safetrack.watch.domain.repository.MonitoringRepository
import com.safetrack.watch.services.withWakeLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The watch's own GNSS/fused location: the primary location source for the child.
 * Periodic updates are sent to SafeTrack; the newest fix is also kept for SOS.
 *
 * Adaptive, to keep the guardian's map live without draining the battery:
 *  - moving: high-accuracy fixes every [MOVING_INTERVAL_MS], a record each
 *    [MOVE_SAVE_M] (the guardian sees the trip as it happens);
 *  - still for [STILL_AFTER_MS]: balanced-power fixes every
 *    [STILL_INTERVAL_MS], one record every [STILL_SAVE_MS];
 *  - after an SOS: every [SOS_INTERVAL_MS] for [SOS_BOOST_MS].
 * Each record carries speed and travel mode ([TravelMode]).
 */
class LocationTracker(
    context: Context,
    private val monitoring: MonitoringRepository,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)
    private var callback: LocationCallback? = null
    private val activity = ActivityTracker(appContext)

    private enum class Pace { MOVING, STILL, SOS }
    private var pace = Pace.MOVING
    private var stillSinceMillis: Long? = null
    private var sosBoostUntilMillis = 0L

    private val _latest = MutableStateFlow<WatchLocation?>(null)
    val latest: StateFlow<WatchLocation?> = _latest.asStateFlow()

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestFor(pace: Pace): LocationRequest = when (pace) {
        Pace.SOS -> LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, SOS_INTERVAL_MS)
            .setMinUpdateIntervalMillis(SOS_INTERVAL_MS)
        Pace.MOVING -> LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, MOVING_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
        // Wi-Fi/cell positions are enough to notice the child starting to move.
        Pace.STILL -> LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, STILL_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MOVING_INTERVAL_MS)
    }.setMinUpdateDistanceMeters(0f).setWaitForAccurateLocation(false).build()

    /** Switches between the moving, still and SOS rates. */
    private fun setPace(next: Pace) {
        if (next == pace) return
        Log.i(TAG, "Location pace $pace -> $next")
        pace = next
        if (callback != null) {
            stop()
            start()
        }
    }

    /** After an SOS the guardian follows the child closely for a while. */
    fun boostForSos() {
        sosBoostUntilMillis = System.currentTimeMillis() + SOS_BOOST_MS
        setPace(Pace.SOS)
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (callback != null || !hasPermission()) return
        activity.start()

        val request = requestFor(pace)

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let(::onFix)
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                monitoring.setLocationAvailable(
                    isLocationUsable(availability.isLocationAvailable, lastFixAtMillis, System.currentTimeMillis()),
                )
            }
        }
        callback = cb

        try {
            client.requestLocationUpdates(request, cb, Looper.getMainLooper())
                .addOnFailureListener { e ->
                    Log.w(TAG, "Location updates unavailable", e)
                    // Not running: let the next start() or refreshNow() try again.
                    if (callback === cb) callback = null
                    monitoring.setLocationAvailable(false)
                }
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission missing", e)
            callback = null
        }
    }

    fun stop() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
    }

    /** Stops everything, Activity Recognition included (monitoring ended). */
    fun shutdown() {
        stop()
        activity.stop()
    }

    // The last fix sent to SafeTrack (a "record").
    private var lastSaved: Location? = null

    // The previous usable fix, for a speed when the fix has none.
    private var previousFix: Location? = null

    // When the last usable fix arrived, saved as a record or not.
    private var lastFixAtMillis: Long? = null

    @Volatile
    private var refreshing = false

    /**
     * Called when the network returns: restarts location updates and asks for a
     * new fix straight away, so the watch doesn't wait for the next update.
     */
    @SuppressLint("MissingPermission")
    suspend fun refreshNow() {
        if (refreshing || !hasPermission()) return
        refreshing = true
        stop()
        start()

        val cancel = CancellationTokenSource()
        try {
            val fresh = withTimeoutOrNull(REFRESH_FIX_TIMEOUT_MS) {
                client.getCurrentLocation(
                    CurrentLocationRequest.Builder()
                        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                        .setMaxUpdateAgeMillis(0)
                        .setDurationMillis(REFRESH_FIX_TIMEOUT_MS)
                        .build(),
                    cancel.token,
                ).await()
            }
            if (fresh != null) onFix(fresh, alwaysSave = true)
        } catch (e: Exception) {
            Log.w(TAG, "Location refresh failed", e)
        } finally {
            cancel.cancel()
            refreshing = false
        }
    }

    /**
     * Moving: each fix [MOVE_SAVE_M] or more from the last record is saved, so
     * trips are drawn along the real streets as they happen. Staying still:
     * one record every [STILL_SAVE_MS].
     */
    private fun shouldSave(location: Location): Boolean {
        val last = lastSaved ?: return true
        if (location.time - last.time >= STILL_SAVE_MS) return true
        val moved = location.distanceTo(last)
        // Ignore wobble smaller than the two fixes' accuracy.
        val noise = maxOf(if (location.hasAccuracy()) location.accuracy else 0f, if (last.hasAccuracy()) last.accuracy else 0f)
        return moved >= maxOf(MOVE_SAVE_M, noise)
    }

    private fun onFix(location: Location, alwaysSave: Boolean = false) {
        if (location.hasAccuracy() && location.accuracy > MAX_ACCEPTED_ACCURACY_M) {
            Log.d(TAG, "Ignoring coarse fix (${location.accuracy} m)")
            return
        }
        val now = System.currentTimeMillis()
        val fix = location.toWatchLocation(previousFix, now)
        previousFix = location
        _latest.value = fix
        lastFixAtMillis = now
        // Any usable fix means location works, even if this one isn't saved.
        monitoring.setLocationAvailable(true)
        updatePace(fix, now)
        if (!alwaysSave && !shouldSave(location)) return
        // The refresh after a restart or reconnect and the regular updates can
        // deliver the same fix; record it once.
        if (lastSaved?.time == location.time) return
        lastSaved = location
        scope.launch {
            withWakeLock(appContext, "location") { monitoring.reportLocation(fix) }
        }
    }

    /**
     * The latest available location for an SOS: a fresh fix if one arrives
     * quickly, otherwise the newest recent fix. Null if nothing recent is known;
     * the SOS is still sent without it.
     */
    @SuppressLint("MissingPermission")
    suspend fun locationForSos(): WatchLocation? {
        if (!hasPermission()) return recentKnown()

        val cancel = CancellationTokenSource()
        val fresh = try {
            withTimeoutOrNull(SOS_FIX_TIMEOUT_MS) {
                client.getCurrentLocation(
                    CurrentLocationRequest.Builder()
                        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                        .setMaxUpdateAgeMillis(SOS_MAX_FIX_AGE_MS)
                        .setDurationMillis(SOS_FIX_TIMEOUT_MS)
                        .build(),
                    cancel.token,
                ).await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "SOS location request failed", e)
            null
        } finally {
            cancel.cancel()
        }

        if (fresh != null) {
            val fix = fresh.toWatchLocation()
            _latest.value = fix
            return fix
        }
        return recentKnown()
    }

    /** The newest fix no older than [RECENT_FIX_MAX_AGE_MS]. */
    @SuppressLint("MissingPermission")
    suspend fun recentKnown(): WatchLocation? {
        val now = System.currentTimeMillis()
        _latest.value?.takeIf { now - it.recordedAtMillis <= RECENT_FIX_MAX_AGE_MS }?.let { return it }
        if (!hasPermission()) return null
        return try {
            client.lastLocation.await()
                ?.takeIf { now - it.time <= RECENT_FIX_MAX_AGE_MS }
                ?.toWatchLocation()
        } catch (e: Exception) {
            null
        }
    }

    private fun updatePace(fix: WatchLocation, now: Long) {
        if (pace == Pace.SOS && now < sosBoostUntilMillis) return
        val moving = fix.travelMode?.let(TravelMode::isMoving) ?: false
        if (moving) {
            stillSinceMillis = null
            setPace(Pace.MOVING)
        } else {
            val since = stillSinceMillis ?: now.also { stillSinceMillis = it }
            setPace(if (now - since >= STILL_AFTER_MS) Pace.STILL else Pace.MOVING)
        }
    }

    private fun Location.toWatchLocation(previous: Location? = null, now: Long = System.currentTimeMillis()): WatchLocation {
        val accuracyMeters = if (hasAccuracy()) accuracy else null
        // The fix's own Doppler speed, else the speed since the previous fix.
        val elapsed = if (previous != null) time - previous.time else 0L
        // Wi-Fi/cell fixes indoors have no speed and wander by their accuracy:
        // a move smaller than that is no movement, not an unknown mode.
        val noise = maxOf(accuracyMeters ?: 0f, if (previous?.hasAccuracy() == true) previous.accuracy else 0f)
        val speed = when {
            hasSpeed() -> speed
            previous != null && elapsed in 3_000L..STILL_SAVE_MS + 60_000L && distanceTo(previous) <= noise -> 0f
            previous != null && elapsed in 3_000L..120_000L -> distanceTo(previous) / (elapsed / 1000f)
            else -> null
        }
        val estimate = TravelMode.combine(TravelMode.fromSpeed(speed, accuracyMeters), ActivityTracker.latest, now, speed)
        return WatchLocation(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            recordedAtMillis = time,
            speedMps = speed,
            headingDegrees = if (hasBearing()) bearing else null,
            travelMode = estimate.mode,
            modeConfidence = estimate.confidence,
        )
    }

    companion object {
        private const val TAG = "LocationTracker"

        /**
         * Location checks while moving; only some become records (see
         * shouldSave). 10 s instead of 5 s roughly halves the extra GPS
         * battery use and still looks continuous on the guardian's map.
         */
        const val MOVING_INTERVAL_MS = 10_000L
        const val MIN_UPDATE_INTERVAL_MS = 5_000L

        /** Still this long: switch to [STILL_INTERVAL_MS] balanced-power checks. */
        const val STILL_AFTER_MS = 2 * 60_000L
        const val STILL_INTERVAL_MS = 60_000L

        /** After an SOS: every 5 s for 10 minutes. */
        const val SOS_INTERVAL_MS = 5_000L
        const val SOS_BOOST_MS = 10 * 60_000L

        /** Moving: a record every time the child is this far from the last one. */
        const val MOVE_SAVE_M = 15f

        /** Staying still: one record this often, as before. */
        const val STILL_SAVE_MS = 3 * 60_000L

        /** Fixes this coarse are not useful for safe zones. */
        const val MAX_ACCEPTED_ACCURACY_M = 1_000f

        const val SOS_FIX_TIMEOUT_MS = 6_000L
        const val SOS_MAX_FIX_AGE_MS = 30_000L
        const val RECENT_FIX_MAX_AGE_MS = 30 * 60_000L

        const val REFRESH_FIX_TIMEOUT_MS = 30_000L

        /** A fix this recent still counts as location working. */
        const val USABLE_FIX_MAX_AGE_MS = 2 * 60_000L

        /** The system can briefly report no location between otherwise good fixes. */
        fun isLocationUsable(systemAvailable: Boolean, lastFixAtMillis: Long?, nowMillis: Long): Boolean =
            systemAvailable ||
                (lastFixAtMillis != null && nowMillis - lastFixAtMillis <= USABLE_FIX_MAX_AGE_MS)
    }
}
