package com.safetrack.watch

import com.safetrack.watch.app.ui.components.timeAgo
import com.safetrack.watch.domain.model.SafeZoneStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `relative times are child friendly`() {
        val now = 10_000_000_000L
        assertEquals("Not yet", timeAgo(null, now))
        assertEquals("Just now", timeAgo(now - 20_000, now))
        assertEquals("5 min ago", timeAgo(now - 5 * 60_000, now))
        assertEquals("2 h ago", timeAgo(now - 2 * 3_600_000, now))
        assertEquals("Just now", timeAgo(now + 60_000, now)) // clock skew
    }
}
