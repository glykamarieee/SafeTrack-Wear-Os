package com.safetrack.watch.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
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
import com.safetrack.watch.app.ui.components.Eyebrow
import com.safetrack.watch.app.ui.components.InfoCard
import com.safetrack.watch.app.ui.components.SafeTrackBrand
import com.safetrack.watch.app.ui.components.SafeZoneBadge
import com.safetrack.watch.app.ui.components.ScreenTitle
import com.safetrack.watch.app.ui.components.SoftDivider
import com.safetrack.watch.app.ui.components.StatusPill
import com.safetrack.watch.app.ui.components.StatusRow
import com.safetrack.watch.app.ui.components.connectionLook
import com.safetrack.watch.app.ui.components.safeZoneLook
import com.safetrack.watch.app.ui.components.timeAgo
import com.safetrack.watch.domain.model.ConnectionState
import com.safetrack.watch.domain.model.MonitoringSnapshot
import com.safetrack.watch.domain.model.SafeZoneStatus

@Composable
private fun TrackVisibility(viewModel: DashboardViewModel) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onVisible() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onHidden() }
}

/**
 * Child dashboard. The hierarchy is intentionally simple for a small round
 * display: safety state -> SOS -> device health -> latest location.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenSafeZone: () -> Unit,
    onOpenStatus: () -> Unit,
    /** False when "Allow all the time" location is missing: monitoring cannot restart after a reboot. */
    backgroundLocationGranted: Boolean = true,
    onAllowBackgroundLocation: () -> Unit = {},
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SafeTrackBrand(compact = true)
                    Text(
                        "Hi, $name",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SafeTrackColors.TextPrimary,
                    )
                    Text(
                        "Monitoring is active on this watch",
                        fontSize = 10.sp,
                        color = SafeTrackColors.TextMuted,
                    )
                }
            }

            item {
                val (zoneColor, _) = safeZoneLook(snapshot.safeZone)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(zoneColor.copy(alpha = 0.10f))
                        .clickable(role = Role.Button, onClick = onOpenSafeZone)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SafeZoneBadge(snapshot.safeZone)
                        Text(
                            safeZoneDetail(snapshot.safeZone),
                            fontSize = 10.sp,
                            color = SafeTrackColors.TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            "Tap for details",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = zoneColor,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }

            item { SosHoldButton(onHoldComplete = viewModel::holdCompleted, size = 124.dp) }

            item {
                Text(
                    "Hold the SOS control until the ring completes.\nThe shake gesture uses the same confirmation flow.",
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    color = SafeTrackColors.TextFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
            }

            if (!backgroundLocationGranted) {
                item {
                    InfoCard(
                        onClick = onAllowBackgroundLocation,
                        containerColor = SafeTrackColors.Warning.copy(alpha = 0.10f),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Eyebrow("Location access", color = SafeTrackColors.Warning)
                            Text(
                                "Tracking stops if the watch restarts.",
                                color = SafeTrackColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                "Tap to allow location all the time.",
                                color = SafeTrackColors.TextMuted,
                                fontSize = 9.sp,
                                lineHeight = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }

            if (snapshot.pendingSosCount > 0) {
                item {
                    InfoCard(containerColor = SafeTrackColors.Warning.copy(alpha = 0.10f)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Eyebrow("Needs connection", color = SafeTrackColors.Warning)
                            Text(
                                "An SOS is waiting to send.",
                                color = SafeTrackColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                "SafeTrack will retry when communication returns.",
                                color = SafeTrackColors.TextMuted,
                                fontSize = 9.sp,
                                lineHeight = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }

            item {
                val (connectionColor, connectionLabel) = connectionLook(snapshot.connection)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onOpenStatus)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                "WATCH STATUS",
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.9.sp,
                                color = SafeTrackColors.TextFaint,
                            )
                            Text(
                                "Device health",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = SafeTrackColors.TextPrimary,
                            )
                        }
                        StatusPill(connectionLabel, connectionColor)
                    }
                    Spacer(Modifier.height(7.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        MiniMetric("BATTERY", snapshot.device.batteryPercent?.let { "$it%" } ?: "--")
                        MiniMetric("NETWORK", snapshot.device.network.label)
                        MiniMetric("SYNC", timeAgo(snapshot.lastSyncAtMillis))
                    }
                }
            }

            item {
                InfoCard(onClick = onOpenStatus) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Eyebrow("Latest available location", color = SafeTrackColors.TextFaint)
                        Text(
                            lastLocationText(snapshot),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (snapshot.locationAvailable) SafeTrackColors.TextPrimary else SafeTrackColors.Warning,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = SafeTrackColors.TextFaint)
        Text(
            value,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = SafeTrackColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

private fun lastLocationText(snapshot: MonitoringSnapshot): String = when {
    !snapshot.locationAvailable -> "Location currently unavailable"
    snapshot.lastLocationAtMillis == null -> "Waiting for the first location update"
    else -> "Updated " + timeAgo(snapshot.lastLocationAtMillis).replaceFirstChar { it.lowercase() }
}

private fun safeZoneDetail(status: SafeZoneStatus): String = when (status) {
    is SafeZoneStatus.Inside -> status.zoneName
    SafeZoneStatus.Outside -> "The latest location is outside guardian-defined safe zones"
    is SafeZoneStatus.Unavailable -> when (status.reason) {
        SafeZoneStatus.Reason.NO_SAFE_ZONE -> "No safe zone has been configured"
        SafeZoneStatus.Reason.NO_LOCATION -> "Waiting for an available location"
        SafeZoneStatus.Reason.NOT_CHECKED -> "Checking the latest available location"
    }
}

/** Safe-Zone Status (read-only: safe zones are managed by the guardian). */
@Composable
fun SafeZoneScreen(viewModel: DashboardViewModel) {
    TrackVisibility(viewModel)
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SafeTrackBrand(compact = true)
                    ScreenTitle("Safe-zone status")
                }
            }
            item { SafeZoneBadge(snapshot.safeZone, large = true) }
            item {
                Text(
                    safeZoneDetail(snapshot.safeZone),
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SafeTrackColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Eyebrow("Location used", color = SafeTrackColors.TextFaint)
                    Text(
                        "Latest update ${timeAgo(snapshot.lastLocationAtMillis, never = "not received yet").lowercase()}",
                        fontSize = 11.sp,
                        color = SafeTrackColors.TextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                InfoCard(containerColor = SafeTrackColors.SurfaceDark) {
                    Text(
                        "Safe zones are created and changed by the linked Guardian. This watch only shows the current available status.",
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = SafeTrackColors.TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Device / connection status. */
@Composable
fun StatusScreen(viewModel: DashboardViewModel) {
    TrackVisibility(viewModel)
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val watchId by viewModel.watchId.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    val (connectionColor, connectionLabel) = connectionLook(snapshot.connection)

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SafeTrackBrand(compact = true)
                    ScreenTitle("Watch status")
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(connectionColor.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(connectionColor),
                    )
                }
            }

            item { StatusPill(connectionLabel, connectionColor) }

            if (snapshot.connection == ConnectionState.DISCONNECTED) {
                item {
                    Text(
                        "Monitoring will resume when communication returns.",
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = SafeTrackColors.Warning,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }

            item {
                InfoCard {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        StatusRow("Last sync", timeAgo(snapshot.lastSyncAtMillis))
                        SoftDivider()
                        val battery = snapshot.device.batteryPercent?.let { "$it%" } ?: "--"
                        StatusRow("Battery", if (snapshot.device.isCharging) "$battery • charging" else battery)
                        SoftDivider()
                        StatusRow("Network", snapshot.device.network.label)
                        SoftDivider()
                        StatusRow(
                            "Location",
                            if (snapshot.locationAvailable) "Available" else "Unavailable",
                            dotColor = if (snapshot.locationAvailable) SafeTrackColors.Safe else SafeTrackColors.Warning,
                        )
                    }
                }
            }

            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 8.dp)) {
                    Eyebrow("Watch ID", color = SafeTrackColors.TextFaint)
                    Text(
                        watchId,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SafeTrackColors.Mint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
