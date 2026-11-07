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
import com.safetrack.watch.services.sensors.HeadingTracker
import com.safetrack.watch.services.withWakeLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
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
 *  - after an SOS: every [SOS_INTERVAL_MS] for [SOS_BOOST_MS];
 *  - while a guardian has the map open: every [LIVE_INTERVAL_MS], each fix
 *    sent to the live map (not saved). Every [LIVE_PROBE_MS] the watch asks
 *    whether anyone is watching, with the fix it already has.
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

    // LIVE_STILL: a guardian is watching but the child has not moved for
    // [LIVE_STILL_AFTER_MS]; GPS every [LIVE_STILL_MS] saves battery.
    private enum class Pace { MOVING, STILL, SOS, LIVE, LIVE_STILL }
    private var pace = Pace.MOVING
    private var stillSinceMillis: Long? = null
    private var sosBoostUntilMillis = 0L

    // Live map: a guardian is watching (answer of the last push_watch_live).
    @Volatile
    private var liveWanted = false
    @Volatile
    private var liveInFlight = false
    // The newest fix that arrived while a push was on its way: sent right
    // after it, so a slow network never drops fixes (no lag while running).
    @Volatile
    private var livePending: WatchLocation? = null
    private var probeJob: Job? = null

    // Which way the watch faces, for the arrow on the guardian's map (only
    // while a guardian watches). Turning in place is sent at once.
    private val compass = HeadingTracker(appContext) { heading -> onCompass(heading) }
    private val testLog = LiveTestLog(appContext)

    private val _latest = MutableStateFlow<WatchLocation?>(null)
    val latest: StateFlow<WatchLocation?> = _latest.asStateFlow()

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestFor(pace: Pace): LocationRequest = when (pace) {
        Pace.LIVE -> LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LIVE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(LIVE_INTERVAL_MS)
        Pace.LIVE_STILL -> LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LIVE_STILL_MS)
            .setMinUpdateIntervalMillis(LIVE_STILL_MS)
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
        testLog.write("pace", "from" to pace, "to" to next)
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
        startProbe()

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
        compass.stop()
        probeJob?.cancel()
        probeJob = null
        liveWanted = false
    }

    /** Asks every [LIVE_PROBE_MS] whether a guardian is watching (cheap: no GPS). */
    private fun startProbe() {
        if (probeJob?.isActive == true) return
        probeJob = scope.launch {
            while (isActive) {
                delay(LIVE_PROBE_MS)
                if (!liveWanted) _latest.value?.let(::pushLive)
            }
        }
    }

    // The last fix sent to the live map.
    private var lastLiveSent: Location? = null
    private var lastLiveSentAtMillis = 0L
    private var lastLiveHeading: Float? = null
    // The last sharp (GPS-quality) fix, so a coarse Wi-Fi/cell fix right after
    // it is not sent: it would pull the child's marker 50-300 m away.
    private var lastSharpAtMillis = 0L

    /**
     * Moving: every fix. Still: one every [LIVE_STILL_MS], since the marker does
     * not move; a speed, a move of [LIVE_MOVE_M] or a turn of [LIVE_TURN_DEG]
     * sends at once.
     */
    private fun liveDue(fix: WatchLocation, location: Location, now: Long): Boolean {
        val accuracy = fix.accuracyMeters
        if (accuracy != null && accuracy <= LIVE_SHARP_M) lastSharpAtMillis = now
        if (accuracy != null && accuracy > LIVE_COARSE_M && now - lastSharpAtMillis < LIVE_SHARP_MS) {
            testLog.write("skip", "why" to "coarse", "acc" to accuracy)
            return false
        }
        val still = fix.travelMode == TravelMode.STILL && (fix.speedMps ?: 0f) < TravelMode.STILL_BELOW_MPS
        val moved = lastLiveSent?.let { location.distanceTo(it) >= LIVE_MOVE_M } ?: true
        val turned = turnedSinceLastSent(fix.headingDegrees)
        if (!still || moved || turned || now - lastLiveSentAtMillis >= LIVE_STILL_MS) {
            lastLiveSent = location
            lastLiveSentAtMillis = now
            return true
        }
        return false
    }

    private fun turnedSinceLastSent(heading: Float?): Boolean {
        val last = lastLiveHeading
        return heading != null && (last == null || HeadingTracker.angleBetween(heading, last) >= LIVE_TURN_DEG)
    }

    /** The compass moved: a child turning in place is sent at once (rate-limited). */
    private fun onCompass(heading: Float) {
        if (!liveWanted) return
        val now = System.currentTimeMillis()
        val fix = _latest.value ?: return
        val still = (fix.speedMps ?: 0f) < FACING_FROM_GPS_MPS
        if (!still || now - lastLiveSentAtMillis < LIVE_TURN_MIN_MS || !turnedSinceLastSent(heading)) return
        if (now - fix.recordedAtMillis > LIVE_TURN_MAX_FIX_AGE_MS) return
        lastLiveSentAtMillis = now
        // Same place, new direction; a newer time so the map takes it.
        pushLive(fix.copy(headingDegrees = heading, recordedAtMillis = now))
    }

    /** One fix to the live map; the answer switches the 1-second pace on or off. */
    private fun pushLive(fix: WatchLocation) {
        if (liveInFlight) {
            livePending = fix
            return
        }
        liveInFlight = true
        if (fix.headingDegrees != null) lastLiveHeading = fix.headingDegrees
        scope.launch {
            val started = System.currentTimeMillis()
            val live = try {
                monitoring.pushLive(fix) == true
            } finally {
                liveInFlight = false
            }
            testLog.write(
                "push",
                "ms" to (System.currentTimeMillis() - started),
                "age" to (System.currentTimeMillis() - fix.recordedAtMillis),
                "live" to live,
                "acc" to fix.accuracyMeters,
                "spd" to fix.speedMps,
                "hd" to fix.headingDegrees,
                "mode" to fix.travelMode,
                "lat" to fix.latitude,
                "lng" to fix.longitude,
            )
            // Send the newest fix that arrived meanwhile (older ones are superseded).
            livePending?.let { pending ->
                livePending = null
                if (live) withContext(Dispatchers.Main) { pushLive(pending) }
            }
            if (live == liveWanted) return@launch
            liveWanted = live
            Log.i(TAG, if (live) "Guardian watching: live pace" else "Guardian left: normal pace")
            testLog.write("live", "on" to live)
            withContext(Dispatchers.Main) {
                if (live) {
                    compass.start()
                    setPace(Pace.LIVE)
                } else {
                    compass.stop()
                    lastLiveHeading = null
                    _latest.value?.let { updatePace(it, System.currentTimeMillis()) }
                }
            }
        }
    }

    /**
     * Which way the child faces: the GPS direction of travel while walking or
     * running, otherwise the watch's compass (live only). Null when unknown.
     */
    private fun facing(location: Location, speedMps: Float?): Float? {
        val gps = if (location.hasBearing() && (speedMps ?: 0f) >= FACING_FROM_GPS_MPS) location.bearing else null
        return gps ?: if (liveWanted) compass.heading else null
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
        if (liveWanted) compass.updatePosition(location.latitude, location.longitude, location.altitude)
        testLog.write(
            "fix",
            "acc" to fix.accuracyMeters,
            "spd" to fix.speedMps,
            "hd" to fix.headingDegrees,
            "gps" to (if (location.hasBearing()) location.bearing else null),
            "mode" to fix.travelMode,
            "conf" to fix.modeConfidence,
            "pace" to pace,
            "live" to liveWanted,
            "provider" to location.provider,
            "lat" to fix.latitude,
            "lng" to fix.longitude,
        )
        // Any usable fix means location works, even if this one isn't saved.
        monitoring.setLocationAvailable(true)
        updatePace(fix, now)
        if (liveWanted && liveDue(fix, location, now)) pushLive(fix)
        if (!alwaysSave && !shouldSave(location)) return
        // The refresh after a restart or reconnect and the regular updates can
        // deliver the same fix; record it once.
        if (lastSaved?.time == location.time) return
        lastSaved = location
        scope.launch {
            withWakeLock(appContext, "location") { monitoring.reportLocation(fix) }
            testLog.write("record", "zone" to monitoring.snapshot.value.safeZone, "acc" to fix.accuracyMeters)
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
        if (liveWanted) {
            val moving = fix.travelMode?.let(TravelMode::isMoving) ?: false ||
                (fix.speedMps ?: 0f) >= TravelMode.STILL_BELOW_MPS
            if (moving) stillSinceMillis = null
            val since = stillSinceMillis ?: now.also { if (!moving) stillSinceMillis = it }
            setPace(if (!moving && now - since >= LIVE_STILL_AFTER_MS) Pace.LIVE_STILL else Pace.LIVE)
            return
        }
        if (now < sosBoostUntilMillis) {
            setPace(Pace.SOS)
            return
        }
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
            headingDegrees = facing(this, speed),
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

        /** A guardian has the map open: a fix every second, each sent live. */
        const val LIVE_INTERVAL_MS = 1_000L

        /** How often the watch asks whether a guardian is watching. */
        const val LIVE_PROBE_MS = 20_000L

        /** Live and still: a fix this often keeps the map "Live"; moving sends every fix. */
        const val LIVE_STILL_MS = 5_000L
        const val LIVE_MOVE_M = 5f

        /** Live and still this long: GPS every [LIVE_STILL_MS] until the child moves. */
        const val LIVE_STILL_AFTER_MS = 30_000L

        /** Live: turning this much is sent at once (no more than every [LIVE_TURN_MIN_MS]). */
        const val LIVE_TURN_DEG = 15f
        const val LIVE_TURN_MIN_MS = 700L
        /** A turn is sent with the last position only while that is this recent. */
        const val LIVE_TURN_MAX_FIX_AGE_MS = 10_000L

        /** Live: a fix coarser than [LIVE_COARSE_M] waits while a [LIVE_SHARP_M] one is recent. */
        const val LIVE_COARSE_M = 50f
        const val LIVE_SHARP_M = 20f
        const val LIVE_SHARP_MS = 10_000L

        /** Walking speed: from here the direction of travel is where the child faces. */
        const val FACING_FROM_GPS_MPS = 1f

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
