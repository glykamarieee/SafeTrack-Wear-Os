package com.safetrack.watch.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.safetrack.watch.app.theme.SafeTrackColors
import com.safetrack.watch.app.ui.components.InfoCard
import com.safetrack.watch.app.ui.components.SafeZoneBadge
import com.safetrack.watch.app.ui.components.ScreenTitle
import com.safetrack.watch.app.ui.components.StatusRow
import com.safetrack.watch.app.ui.components.connectionLook
import com.safetrack.watch.app.ui.components.timeAgo
import com.safetrack.watch.domain.model.ConnectionState
import com.safetrack.watch.domain.model.MonitoringSnapshot
import com.safetrack.watch.domain.model.SafeZoneStatus

@Composable
private fun TrackVisibility(viewModel: DashboardViewModel) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onVisible() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onHidden() }
}

/** Child Dashboard: safe zone, SOS, connection, battery/network, last location. */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenSafeZone: () -> Unit,
    onOpenStatus: () -> Unit,
) {
    TrackVisibility(viewModel)
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val name by viewModel.childName.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ScreenTitle("SafeTrack")
                    Text("Hello, $name", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }

            item {
                InfoCard(onClick = onOpenSafeZone) { SafeZoneBadge(snapshot.safeZone) }
            }

            item { SosHoldButton(onHoldComplete = viewModel::holdCompleted) }

            if (snapshot.pendingSosCount > 0) {
                item {
                    InfoCard {
                        Text(
                            "SOS waiting to send.\nIt will send when communication is available.",
                            color = SafeTrackColors.Warning,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                InfoCard(onClick = onOpenStatus) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        val (color, label) = connectionLook(snapshot.connection)
                        StatusRow("Connection", label, dotColor = color)
                        StatusRow("Battery", snapshot.device.batteryPercent?.let { "$it%" } ?: "--")
                        StatusRow("Network", snapshot.device.network.label)
                    }
                }
            }

            item {
                InfoCard {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("Last Location", fontSize = 11.sp, color = SafeTrackColors.TextMuted)
                        Text(
                            lastLocationText(snapshot),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (snapshot.locationAvailable) Color.White else SafeTrackColors.Warning,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

private fun lastLocationText(snapshot: MonitoringSnapshot): String = when {
    !snapshot.locationAvailable -> "Location currently unavailable."
    snapshot.lastLocationAtMillis == null -> "Waiting for location"
    else -> "Updated " + timeAgo(snapshot.lastLocationAtMillis).replaceFirstChar { it.lowercase() }
}

/** Safe-Zone Status (read-only: safe zones are managed by the guardian). */
@Composable
fun SafeZoneScreen(viewModel: DashboardViewModel) {
    TrackVisibility(viewModel)
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SafeZoneBadge(snapshot.safeZone, large = true) }
            item {
                val (label, detail) = when (val zone = snapshot.safeZone) {
                    is SafeZoneStatus.Inside -> "Zone:" to zone.zoneName
                    SafeZoneStatus.Outside -> "Zone:" to "Outside your safe zones"
                    is SafeZoneStatus.Unavailable -> "" to when (zone.reason) {
                        SafeZoneStatus.Reason.NO_SAFE_ZONE -> "No safe zone set yet"
                        else -> "Location unavailable"
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (label.isNotEmpty()) Text(label, fontSize = 12.sp, color = SafeTrackColors.TextMuted)
                    Text(detail, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                }
            }
            item {
                Text(
                    "Based on your last location, " + timeAgo(snapshot.lastLocationAtMillis, never = "not received yet")
                        .replaceFirstChar { it.lowercase() } + ".",
                    fontSize = 11.sp,
                    color = SafeTrackColors.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            item {
                Text(
                    "Your guardian sets your safe zones.",
                    fontSize = 11.sp,
                    color = SafeTrackColors.TextMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Basic Device / Connection Status. */
@Composable
fun StatusScreen(viewModel: DashboardViewModel) {
    TrackVisibility(viewModel)
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val watchId by viewModel.watchId.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { ScreenTitle("SafeTrack") }
            item {
                InfoCard {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        val (color, label) = connectionLook(snapshot.connection)
                        StatusRow("Connection", label, dotColor = color)
                        StatusRow("Last Sync", timeAgo(snapshot.lastSyncAtMillis))
                    }
                }
            }
            if (snapshot.connection == ConnectionState.DISCONNECTED) {
                item {
                    Text(
                        "Monitoring will resume\nwhen communication returns.",
                        fontSize = 12.sp,
                        color = SafeTrackColors.Warning,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                InfoCard {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        val battery = snapshot.device.batteryPercent?.let { "$it%" } ?: "--"
                        StatusRow("Battery", if (snapshot.device.isCharging) "$battery ⚡" else battery)
                        StatusRow("Network", snapshot.device.network.label)
                        StatusRow("Location", if (snapshot.locationAvailable) "On" else "Unavailable")
                    }
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(2.dp))
                    Text("Watch ID", fontSize = 11.sp, color = SafeTrackColors.TextMuted)
                    Text(watchId, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = SafeTrackColors.Mint)
                }
            }
        }
    }
}
