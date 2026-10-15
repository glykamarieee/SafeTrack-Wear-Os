package com.safetrack.watch.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.wear.compose.material3.AppScaffold
import com.safetrack.watch.app.theme.SafeTrackTheme
import com.safetrack.watch.app.ui.components.SafeTrackTimeText
import com.safetrack.watch.app.ui.connect.ConnectedScreen
import com.safetrack.watch.app.ui.dashboard.DashboardContent
import com.safetrack.watch.app.ui.dashboard.SafeZoneContent
import com.safetrack.watch.app.ui.dashboard.StatusContent
import com.safetrack.watch.app.ui.permissions.PermissionsScreen
import com.safetrack.watch.app.ui.sos.SosScreen
import com.safetrack.watch.app.ui.tour.WatchTour
import com.safetrack.watch.domain.model.ConnectionState
import com.safetrack.watch.domain.model.DeviceStatus
import com.safetrack.watch.domain.model.MonitoringSnapshot
import com.safetrack.watch.domain.model.NetworkType
import com.safetrack.watch.domain.model.SafeZoneStatus
import com.safetrack.watch.domain.model.SosActivation
import com.safetrack.watch.domain.model.SosState
import kotlinx.coroutines.delay

/**
 * DEBUG BUILDS ONLY. Shows one screen with sample data, so every state can be
 * checked on a watch or emulator without pairing or sending anything:
 *
 *   adb shell am start -n com.safetrack.watch/.debug.ScreenPreviewActivity --es screen dash_outside
 *
 * Nothing here talks to SafeTrack; buttons do nothing.
 */
class ScreenPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = intent.getStringExtra("screen") ?: "dash"
        setContent {
            SafeTrackTheme {
                AppScaffold(timeText = { SafeTrackTimeText() }) {
                    Preview(screen)
                }
            }
        }
    }
}

private val now = System.currentTimeMillis()

private val inside = MonitoringSnapshot(
    connection = ConnectionState.CONNECTED,
    lastSyncAtMillis = now - 30_000,
    safeZone = SafeZoneStatus.Inside("Home"),
    lastLocationAtMillis = now - 2 * 60_000,
    device = DeviceStatus(76, false, NetworkType.WIFI),
)

@Composable
private fun Preview(screen: String) {
    when (screen) {
        "dash" -> Dash(inside, "Mia Santos")
        "dash_outside" -> Dash(inside.copy(safeZone = SafeZoneStatus.Outside, device = DeviceStatus(54, false, NetworkType.MOBILE)), "Mia")
        "dash_low" -> Dash(inside.copy(device = DeviceStatus(14, true, NetworkType.WIFI), connection = ConnectionState.CONNECTING), "Mia")
        "dash_offline" -> Dash(
            inside.copy(
                connection = ConnectionState.DISCONNECTED,
                safeZone = SafeZoneStatus.Unavailable(SafeZoneStatus.Reason.NO_LOCATION),
                locationAvailable = false,
                pendingSosCount = 1,
                device = DeviceStatus(41, false, NetworkType.NONE),
            ),
            "Mia",
            background = false,
        )
        "dash_long" -> Dash(
            inside.copy(safeZone = SafeZoneStatus.Inside("Grandma and Grandpa's house in Quezon City")),
            "Maximiliana Alexandria Dela Cruz",
        )
        "zone" -> SafeZoneContent(inside)
        "zone_outside" -> SafeZoneContent(inside.copy(safeZone = SafeZoneStatus.Outside))
        "zone_none" -> SafeZoneContent(inside.copy(safeZone = SafeZoneStatus.Unavailable(SafeZoneStatus.Reason.NO_SAFE_ZONE)))
        "status" -> StatusContent(inside, "ST-WATCH-F7MU6729")
        "status_offline" -> StatusContent(inside.copy(connection = ConnectionState.DISCONNECTED, device = DeviceStatus(9, true, NetworkType.NONE)), "ST-WATCH-F7MU6729")
        "connected" -> ConnectedScreen("Mia Santos") {}
        "perm_fg" -> PermissionsScreen(needsForeground = true, onChanged = {}, onSkipBackground = {})
        "perm_bg" -> PermissionsScreen(needsForeground = false, onChanged = {}, onSkipBackground = {})
        "tour" -> WatchTour {}
        "countdown" -> {
            var seconds by remember { mutableIntStateOf(3) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(1_000)
                    seconds = if (seconds == 1) 3 else seconds - 1
                }
            }
            SosScreen(SosState.Countdown(SosActivation.TAP_AND_HOLD, seconds), onCancel = {}, onDismiss = {})
        }
        "sending" -> SosScreen(SosState.Sending, {}, {})
        "sent" -> SosScreen(SosState.Sent(locationIncluded = true), {}, {})
        "pending" -> SosScreen(SosState.Pending(locationIncluded = false), {}, {})
        "cancelled" -> SosScreen(SosState.Cancelled, {}, {})
    }
}

@Composable
private fun Dash(snapshot: MonitoringSnapshot, name: String, background: Boolean = true) {
    DashboardContent(
        snapshot = snapshot,
        name = name,
        onHoldComplete = {},
        onOpenSafeZone = {},
        onOpenStatus = {},
        backgroundLocationGranted = background,
    )
}
