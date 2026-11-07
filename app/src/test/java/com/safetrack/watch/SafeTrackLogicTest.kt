package com.safetrack.watch

import com.safetrack.watch.app.ui.components.timeAgo
import com.safetrack.watch.domain.model.SafeZoneStatus
import com.google.android.gms.location.DetectedActivity
import com.safetrack.watch.services.location.ActivityTracker
import com.safetrack.watch.services.location.LocationTracker
import com.safetrack.watch.services.location.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeTrackLogicTest {

    @Test
    fun `maps safe_zone_status results`() {
        assertEquals(SafeZoneStatus.Inside("Home"), SafeZoneStatus.fromServer("inside", "Home"))
        assertEquals(SafeZoneStatus.Inside("Safe zone"), SafeZoneStatus.fromServer("inside", null))
        assertEquals(SafeZoneStatus.Outside, SafeZoneStatus.fromServer("outside", null))
        assertEquals(
            SafeZoneStatus.Unavailable(SafeZoneStatus.Reason.NO_SAFE_ZONE),
            SafeZoneStatus.fromServer("no_safe_zone", null),
        )
        assertEquals(
            SafeZoneStatus.Unavailable(SafeZoneStatus.Reason.NO_LOCATION),
            SafeZoneStatus.fromServer("unavailable", null),
        )
    }

    @Test
    fun `unknown or missing status leaves the safe zone unchanged`() {
        assertNull(SafeZoneStatus.fromServer(null, null))
        assertNull(SafeZoneStatus.fromServer("something_new", "Home"))
    }

    @Test
    fun `a recent fix keeps location available when the system says otherwise`() {
        val now = 10_000_000_000L
        assertTrue(LocationTracker.isLocationUsable(true, null, now))
        assertTrue(LocationTracker.isLocationUsable(false, now - 60_000, now))
        assertFalse(LocationTracker.isLocationUsable(false, now - 3 * 60_000, now))
        assertFalse(LocationTracker.isLocationUsable(false, null, now))
    }

    @Test
    fun `travel mode from speed`() {
        assertEquals(TravelMode.UNKNOWN, TravelMode.fromSpeed(null, 10f).mode)
        assertEquals(TravelMode.STILL, TravelMode.fromSpeed(0.3f, 10f).mode)
        assertEquals(TravelMode.WALKING, TravelMode.fromSpeed(1.4f, 10f).mode)
        assertEquals(TravelMode.RUNNING, TravelMode.fromSpeed(3f, 10f).mode)
        // No bike mode: a sprint is running, faster is a vehicle.
        assertEquals(TravelMode.RUNNING, TravelMode.fromSpeed(4.5f, 10f).mode)
        assertEquals(TravelMode.VEHICLE, TravelMode.fromSpeed(6f, 10f).mode)
        assertEquals(TravelMode.VEHICLE, TravelMode.fromSpeed(12f, 10f).mode)
        // A clear run is confident enough to show at once.
        assertTrue(TravelMode.fromSpeed(3f, 10f).confidence >= 0.7f)
        // A coarse fix lowers the confidence.
        assertTrue(TravelMode.fromSpeed(1.4f, 100f).confidence < TravelMode.fromSpeed(1.4f, 10f).confidence)
    }

    @Test
    fun `activity recognition refines speed unless they clearly disagree`() {
        val now = 10_000_000L
        // A bicycle is shown as a vehicle (there is no bike mode).
        val bike = ActivityTracker.Detected(DetectedActivity.ON_BICYCLE, 80, now - 10_000)
        assertEquals(TravelMode.VEHICLE, TravelMode.combine(TravelMode.fromSpeed(3f, 10f), bike, now, 3f).mode)
        // "Still" while moving 12 m/s: speed wins.
        val still = ActivityTracker.Detected(DetectedActivity.STILL, 90, now - 10_000)
        assertEquals(TravelMode.VEHICLE, TravelMode.combine(TravelMode.fromSpeed(12f, 10f), still, now, 12f).mode)
        // Old or unsure activity is ignored.
        val old = ActivityTracker.Detected(DetectedActivity.IN_VEHICLE, 90, now - 5 * 60_000)
        assertEquals(TravelMode.WALKING, TravelMode.combine(TravelMode.fromSpeed(1.4f, 10f), old, now, 1.4f).mode)
        val unsure = ActivityTracker.Detected(DetectedActivity.IN_VEHICLE, 40, now)
        assertEquals(TravelMode.WALKING, TravelMode.combine(TravelMode.fromSpeed(1.4f, 10f), unsure, now, 1.4f).mode)
    }

    @Test
    fun `relative times are child friendly`() {
        val now = 10_000_000_000L
        assertEquals("Not yet", timeAgo(null, now))
        assertEquals("Just now", timeAgo(now - 20_000, now))
        assertEquals("5 min ago", timeAgo(now - 5 * 60_000, now))
        assertEquals("2 h ago", timeAgo(now - 2 * 3_600_000, now))
        assertEquals("Just now", timeAgo(now + 60_000, now)) // clock skew
    }
}
