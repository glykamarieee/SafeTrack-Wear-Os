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
 */
class LocationTracker(
    context: Context,
    private val monitoring: MonitoringRepository,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)
    private var callback: LocationCallback? = null

    private val _latest = MutableStateFlow<WatchLocation?>(null)
    val latest: StateFlow<WatchLocation?> = _latest.asStateFlow()

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (callback != null || !hasPermission()) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()

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

    // The last fix sent to SafeTrack (a "record").
    private var lastSaved: Location? = null

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
     * Fixes arrive every [UPDATE_INTERVAL_MS]. Moving: each one [MOVE_SAVE_M]
     * or more from the last record is saved, so trips are drawn along the
     * real streets. Staying still: one record every [STILL_SAVE_MS].
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
        val fix = location.toWatchLocation()
        _latest.value = fix
        lastFixAtMillis = System.currentTimeMillis()
        // Any usable fix means location works, even if this one isn't saved.
        monitoring.setLocationAvailable(true)
        if (!alwaysSave && !shouldSave(location)) return
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

    private fun Location.toWatchLocation() = WatchLocation(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        recordedAtMillis = time,
    )

    companion object {
        private const val TAG = "LocationTracker"

        /** Location checks while monitoring; only some become records (see shouldSave). */
        const val UPDATE_INTERVAL_MS = 20_000L
        const val MIN_UPDATE_INTERVAL_MS = 10_000L

        /** Moving: a record every time the child is this far from the last one. */
        const val MOVE_SAVE_M = 25f

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
