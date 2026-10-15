package com.safetrack.watch.app.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.safetrack.watch.app.theme.LocalReduceMotion
import com.safetrack.watch.app.theme.SafeTrackColors
import com.safetrack.watch.app.ui.components.BodyText
import com.safetrack.watch.app.ui.components.Eyebrow
import com.safetrack.watch.app.ui.components.Mascot
import com.safetrack.watch.app.ui.components.PillButton
import com.safetrack.watch.app.ui.components.PillStyle
import com.safetrack.watch.app.ui.components.ScreenTitle
import com.safetrack.watch.app.ui.components.SafeTrackTimeText
import com.safetrack.watch.app.ui.components.SoftCard
import com.safetrack.watch.app.ui.components.SoftDivider
import com.safetrack.watch.app.ui.components.StatusDot
import com.safetrack.watch.app.ui.components.StatusPill
import com.safetrack.watch.app.ui.components.StatusRow
import com.safetrack.watch.app.ui.components.ZoneLook
import com.safetrack.watch.app.ui.components.connectionLook
import com.safetrack.watch.app.ui.components.entrance
import com.safetrack.watch.app.ui.components.firstName
import com.safetrack.watch.app.ui.components.rememberPulse
import com.safetrack.watch.app.ui.components.rememberRipple
import com.safetrack.watch.app.ui.components.roundInset
import com.safetrack.watch.app.ui.components.timeAgo
import com.safetrack.watch.app.ui.components.zoneLook
import com.safetrack.watch.domain.model.ConnectionState
import com.safetrack.watch.domain.model.MonitoringSnapshot
import com.safetrack.watch.domain.model.SafeZoneStatus

@Composable
private fun TrackVisibility(viewModel: DashboardViewModel) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onVisible() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onHidden() }
}

/**
 * Child dashboard. The SOS button sits at the centre with the safe-place ring
 * around it; scrolling down shows a greeting, the safe-place status, the
 * watch's health and the latest location. Scrolls with the bezel; nothing sits
 * in the corners.
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
    DashboardContent(
        snapshot = snapshot,
        name = name,
        onHoldComplete = viewModel::holdCompleted,
        onOpenSafeZone = onOpenSafeZone,
        onOpenStatus = onOpenStatus,
        backgroundLocationGranted = backgroundLocationGranted,
        onAllowBackgroundLocation = onAllowBackgroundLocation,
    )
}

/** The dashboard's look, without the view model (also used by the debug preview). */
@Composable
fun DashboardContent(
    snapshot: MonitoringSnapshot,
    name: String,
    onHoldComplete: () -> Unit,
    onOpenSafeZone: () -> Unit,
    onOpenStatus: () -> Unit,
    backgroundLocationGranted: Boolean = true,
    onAllowBackgroundLocation: () -> Unit = {},
) {
    // Opens with the SOS button centred and the time fully visible; the
    // greeting peeks in below, inviting a scroll.
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)

    Box(Modifier.fillMaxSize().background(SafeTrackColors.Background)) {
        BatteryEdgeRing(snapshot.device.batteryPercent)

        // The time stays put here (no scroll-away); it is drawn on top below.
        ScreenScaffold(scrollState = listState, timeText = {}) { contentPadding ->
            ScalingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    SafetyHero(
                        status = snapshot.safeZone,
                        onHoldComplete = onHoldComplete,
                        modifier = Modifier.entrance(0),
                    )
                }

                item { Greeting(name, Modifier.entrance(1)) }

                item { ZoneChip(snapshot.safeZone, onOpenSafeZone, Modifier.entrance(2)) }

                item {
                    Text(
                        "Hold SOS or shake your watch",
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = SafeTrackColors.TextFaint,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.roundInset(0.08f).entrance(3),
                    )
                }

                if (!backgroundLocationGranted) {
                    item {
                        NoticeCard(
                            icon = Icons.Rounded.LocationOn,
                            title = "Allow location all the time",
                            body = "So SafeTrack keeps working after a restart",
                            onClick = onAllowBackgroundLocation,
                        )
                    }
                }

                if (snapshot.pendingSosCount > 0) {
                    item {
                        NoticeCard(
                            icon = Icons.Rounded.Refresh,
                            title = "SOS waiting to send",
                            body = "It sends when the watch is back online",
                        )
                    }
                }

                item { DeviceTrio(snapshot, onOpenStatus, Modifier.entrance(4)) }

                item { LatestLocationChip(snapshot, onOpenStatus, Modifier.entrance(5)) }
            }
        }

        SafeTrackTimeText(snapshot.device.batteryPercent)
    }
}

@Composable
private fun Greeting(name: String, modifier: Modifier = Modifier) {
    val first = firstName(name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier.roundInset(0.10f),
    ) {
        Mascot(size = 26.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            if (first.isBlank()) "Hi there!" else "Hi, $first!",
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = SafeTrackColors.Forest,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The SOS button inside a ring coloured by the safe-place status. The ring
 * draws itself in when the screen opens, changes colour smoothly, and sends
 * out a soft ripple while the child is inside a safe place.
 */
@Composable
private fun SafetyHero(status: SafeZoneStatus, onHoldComplete: () -> Unit, modifier: Modifier = Modifier) {
    val look = zoneLook(status)
    val reduce = LocalReduceMotion.current
    val ringColor by animateColorAsState(look.ring, tween(600), label = "zone-ring")
    val inside = status is SafeZoneStatus.Inside
    val ripple = rememberRipple(2600, enabled = inside)
    val draw = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { draw.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(150.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2
            val stroke = 5.dp.toPx()
            val inset = stroke / 2 + 1.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            if (inside && ripple > 0f) {
                drawCircle(
                    color = SafeTrackColors.GreenBright.copy(alpha = 0.35f * (1f - ripple)),
                    radius = radius * (0.80f + 0.20f * ripple),
                    style = Stroke(2.dp.toPx()),
                )
            }
            drawArc(
                color = ringColor.copy(alpha = 0.22f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke),
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * draw.value,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        SosHoldButton(onHoldComplete = onHoldComplete, size = 118.dp)
    }
}

/** "You're in a safe place · Home". Opens the safe-place screen. */
@Composable
private fun ZoneChip(status: SafeZoneStatus, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val look = zoneLook(status)
    val background by animateColorAsState(look.soft, tween(600), label = "zone-chip")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .roundInset(0.06f)
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(background)
            .border(1.dp, look.color.copy(alpha = 0.25f), RoundedCornerShape(50))
            .clickable(role = Role.Button, onClickLabel = "Open safe place details", onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        ZoneIcon(status, look, 28.dp)
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(
                look.title,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = SafeTrackColors.Forest,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                look.detail,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                color = SafeTrackColors.TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ZoneIcon(status: SafeZoneStatus, look: ZoneLook, size: Dp) {
    val icon = when (status) {
        is SafeZoneStatus.Inside -> Icons.Rounded.Home
        SafeZoneStatus.Outside -> Icons.Rounded.LocationOn
        is SafeZoneStatus.Unavailable -> Icons.Rounded.Refresh
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White),
    ) {
        Icon(icon, contentDescription = null, tint = look.color, modifier = Modifier.size(size * 0.6f))
    }
}

/** Amber notice card for things the child (or guardian) should fix. */
@Composable
private fun NoticeCard(icon: ImageVector, title: String, body: String, onClick: (() -> Unit)? = null) {
    SoftCard(
        modifier = Modifier.roundInset(0.05f),
        onClick = onClick,
        containerColor = SafeTrackColors.WarningSoft,
        borderColor = SafeTrackColors.WarningFill.copy(alpha = 0.6f),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, contentDescription = null, tint = SafeTrackColors.Warning, modifier = Modifier.size(18.dp))
            Text(
                title,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = SafeTrackColors.Forest,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                body,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = SafeTrackColors.TextMuted,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Battery, connection and location as three round bubbles. Opens Watch status. */
@Composable
private fun DeviceTrio(snapshot: MonitoringSnapshot, onOpenStatus: () -> Unit, modifier: Modifier = Modifier) {
    val battery = snapshot.device.batteryPercent
    val (connectionColor, connectionLabel) = connectionLook(snapshot.connection)
    val locationOk = snapshot.locationAvailable && snapshot.lastLocationAtMillis != null
    val description = buildString {
        append("Watch status. Battery ").append(battery?.let { "$it percent" } ?: "unknown")
        if (snapshot.device.isCharging) append(", charging")
        append(". ").append(connectionLabel).append(", ").append(snapshot.device.network.label)
        append(". Location ").append(if (snapshot.locationAvailable) "available" else "unavailable").append('.')
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClickLabel = "Open watch status", onClick = onOpenStatus)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Bubble { BatteryBubble(battery, snapshot.device.isCharging) }
            Bubble {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    StatusDot(connectionColor, size = 7.dp, blinking = snapshot.connection == ConnectionState.CONNECTING)
                    Text(
                        snapshot.device.network.shortLabel(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SafeTrackColors.Forest,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
            Bubble {
                Icon(
                    if (locationOk) Icons.Rounded.CheckCircle else Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = if (snapshot.locationAvailable) SafeTrackColors.Green else SafeTrackColors.Warning,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            "Watch status \u203A",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = SafeTrackColors.Green,
            maxLines = 1,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

private fun com.safetrack.watch.domain.model.NetworkType.shortLabel(): String = when (this) {
    com.safetrack.watch.domain.model.NetworkType.WIFI -> "Wi-Fi"
    com.safetrack.watch.domain.model.NetworkType.MOBILE -> "LTE"
    com.safetrack.watch.domain.model.NetworkType.NONE -> "None"
    com.safetrack.watch.domain.model.NetworkType.UNKNOWN -> "--"
}

@Composable
private fun Bubble(content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(SafeTrackColors.Surface)
            .border(1.dp, SafeTrackColors.Divider, CircleShape),
    ) { content() }
}

/** A small battery ring that fills when the screen opens and pulses when low. */
@Composable
private fun BatteryBubble(percent: Int?, charging: Boolean) {
    val reduce = LocalReduceMotion.current
    val level = (percent ?: 0).coerceIn(0, 100) / 100f
    val low = percent != null && percent <= 20
    val fill = remember { Animatable(if (reduce) level else 0f) }
    LaunchedEffect(level) { fill.animateTo(level, tween(900, easing = FastOutSlowInEasing)) }
    val pulse = rememberPulse(900, enabled = low, still = 1f)
    val color = if (low) SafeTrackColors.WarningFill else SafeTrackColors.GreenBright

    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        // Charging: the ring breathes softly.
        val charge = rememberPulse(1400, enabled = charging, still = 1f)
        Canvas(Modifier.fillMaxSize().padding(3.dp).graphicsLayer { alpha = (if (low) 0.45f + 0.55f * pulse else 1f) * (0.55f + 0.45f * charge) }) {
            val stroke = 3.dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(SafeTrackColors.Track, 0f, 360f, false, Offset(stroke / 2, stroke / 2), arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * fill.value, false, Offset(stroke / 2, stroke / 2), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(
            percent?.let { "$it" } ?: "--",
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = SafeTrackColors.Forest,
            maxLines = 1,
        )
    }
}

/** "Updated 2 min ago" with a little pop and ripple each time a new fix arrives. */
@Composable
private fun LatestLocationChip(snapshot: MonitoringSnapshot, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val pop = remember { Animatable(1f) }
    val ring = remember { Animatable(1f) }
    val previous = remember { mutableLongHolder(snapshot.lastLocationAtMillis) }
    LaunchedEffect(snapshot.lastLocationAtMillis) {
        val changed = previous.value != null && snapshot.lastLocationAtMillis != previous.value
        previous.value = snapshot.lastLocationAtMillis
        if (changed && !reduce) {
            ring.snapTo(0f)
            pop.snapTo(0.6f)
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            ring.animateTo(1f, tween(700))
        }
    }

    val available = snapshot.locationAvailable
    val title = when {
        !available -> "Location unavailable"
        snapshot.lastLocationAtMillis == null -> "Waiting for location"
        else -> "Latest location"
    }
    val detail = when {
        !available -> "Check location is on"
        snapshot.lastLocationAtMillis == null -> "First update coming soon"
        else -> "Updated " + timeAgo(snapshot.lastLocationAtMillis).replaceFirstChar { it.lowercase() }
    }
    val tint = if (available) SafeTrackColors.Green else SafeTrackColors.Warning

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .roundInset(0.10f)
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(SafeTrackColors.Surface)
            .border(1.dp, SafeTrackColors.Divider, RoundedCornerShape(50))
            .clickable(role = Role.Button, onClickLabel = "Open watch status", onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                if (ring.value < 1f) {
                    drawCircle(
                        color = SafeTrackColors.GreenBright.copy(alpha = 0.5f * (1f - ring.value)),
                        radius = size.minDimension / 2 * (0.6f + 0.6f * ring.value),
                        style = Stroke(2.dp.toPx()),
                    )
                }
            }
            Icon(
                if (available) Icons.Rounded.LocationOn else Icons.Rounded.Warning,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
            )
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.ExtraBold, color = SafeTrackColors.Forest, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, fontSize = 10.sp, lineHeight = 12.sp, color = if (available) SafeTrackColors.TextMuted else SafeTrackColors.Warning, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private class LongHolder(var value: Long?)

private fun mutableLongHolder(initial: Long?) = LongHolder(initial)

/** Safe-place status (read-only: safe places are managed by the guardian). */
@Composable
fun SafeZoneScreen(viewModel: DashboardViewModel) {
    TrackVisibility(viewModel)
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    SafeZoneContent(snapshot)
}

@Composable
fun SafeZoneContent(snapshot: MonitoringSnapshot) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    val look = zoneLook(snapshot.safeZone)

    Box(Modifier.fillMaxSize().background(SafeTrackColors.Background)) {
        ScreenScaffold(scrollState = listState) { contentPadding ->
            ScalingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { Box(Modifier.entrance(0)) { Eyebrow("Safe place") } }
                item { Box(Modifier.entrance(1)) { ZoneBadge(snapshot.safeZone, look) } }
                item {
                    Column(Modifier.roundInset(0.06f).entrance(2), horizontalAlignment = Alignment.CenterHorizontally) {
                        ScreenTitle(look.title)
                        BodyText(look.detail, maxLines = 2)
                    }
                }
                item {
                    StatusPill(
                        "Updated " + timeAgo(snapshot.lastLocationAtMillis, never = "not yet").replaceFirstChar { it.lowercase() },
                        SafeTrackColors.TextFaint,
                        Modifier.entrance(3),
                    )
                }
                item {
                    SoftCard(modifier = Modifier.roundInset(0.05f).entrance(4), containerColor = SafeTrackColors.Mint, borderColor = Color.Transparent) {
                        BodyText("Your guardian sets your safe places. Your watch shows if you're in one.", maxLines = 4)
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneBadge(status: SafeZoneStatus, look: ZoneLook) {
    val inside = status is SafeZoneStatus.Inside
    val ripple = rememberRipple(2400, enabled = inside)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(78.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            if (inside && ripple > 0f) {
                drawCircle(
                    color = SafeTrackColors.GreenBright.copy(alpha = 0.4f * (1f - ripple)),
                    radius = size.minDimension / 2 * (0.7f + 0.3f * ripple),
                    style = Stroke(2.dp.toPx()),
                )
            }
            drawCircle(look.soft, radius = size.minDimension / 2 * 0.72f)
            drawCircle(look.color.copy(alpha = 0.35f), radius = size.minDimension / 2 * 0.72f, style = Stroke(2.dp.toPx()))
        }
        ZoneIcon(status, look, 40.dp)
    }
}

/** Device / connection status. */
@Composable
fun StatusScreen(viewModel: DashboardViewModel, onReplayTour: () -> Unit = {}) {
    TrackVisibility(viewModel)
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val watchId by viewModel.watchId.collectAsStateWithLifecycle()
    StatusContent(snapshot, watchId, onReplayTour)
}

@Composable
fun StatusContent(snapshot: MonitoringSnapshot, watchId: String, onReplayTour: () -> Unit = {}) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    val (connectionColor, connectionLabel) = connectionLook(snapshot.connection)
    val connecting = snapshot.connection == ConnectionState.CONNECTING

    Box(Modifier.fillMaxSize().background(SafeTrackColors.Background)) {
        ScreenScaffold(scrollState = listState) { contentPadding ->
            ScalingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { Box(Modifier.entrance(0)) { ScreenTitle("Watch status") } }

                item {
                    val wave = rememberPulse(1400)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .entrance(1)
                            .size(58.dp)
                            .graphicsLayer {
                                val s = if (snapshot.connection == ConnectionState.CONNECTED) 0.96f + 0.06f * wave else 1f
                                scaleX = s
                                scaleY = s
                            }
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(Color.White, connectionColor.copy(alpha = 0.20f)))),
                    ) {
                        StatusDot(connectionColor, size = 18.dp, blinking = connecting)
                    }
                }

                item { StatusPill(connectionLabel, connectionColor, blinking = connecting) }

                if (snapshot.connection == ConnectionState.DISCONNECTED) {
                    item {
                        Box(Modifier.roundInset(0.08f)) {
                            BodyText("Monitoring resumes when the watch is back online.", color = SafeTrackColors.Warning)
                        }
                    }
                }

                item {
                    SoftCard(modifier = Modifier.roundInset(0.04f)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusRow("Last sync", timeAgo(snapshot.lastSyncAtMillis))
                            SoftDivider()
                            val battery = snapshot.device.batteryPercent?.let { "$it%" } ?: "--"
                            StatusRow("Battery", if (snapshot.device.isCharging) "$battery charging" else battery)
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.roundInset(0.06f)) {
                        Eyebrow("Watch ID", color = SafeTrackColors.TextFaint)
                        Text(
                            watchId,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SafeTrackColors.Forest,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }

                item {
                    PillButton(
                        label = "REPLAY TOUR",
                        onClick = onReplayTour,
                        style = PillStyle.Tonal,
                        modifier = Modifier.roundInset(0.06f).widthIn(max = 160.dp),
                    )
                }
            }
        }
    }
}

/**
 * A thin ring along the screen edge showing the battery level. It fills when
 * the dashboard opens, and is amber and gently pulsing at 20% or below.
 */
@Composable
private fun BatteryEdgeRing(percent: Int?) {
    if (percent == null) return
    val reduce = LocalReduceMotion.current
    val level = percent.coerceIn(0, 100) / 100f
    val low = percent <= 20
    val fill = remember { Animatable(if (reduce) level else 0f) }
    LaunchedEffect(level) { fill.animateTo(level, tween(1100, easing = FastOutSlowInEasing)) }
    val pulse = rememberPulse(1000, enabled = low, still = 1f)
    val color = if (low) SafeTrackColors.WarningFill else SafeTrackColors.GreenBright

    Canvas(
        Modifier
            .fillMaxSize()
            .padding(2.dp)
            .semantics { contentDescription = "Battery $percent percent" },
    ) {
        val stroke = 4.dp.toPx()
        val inset = stroke / 2
        val arc = Size(size.width - stroke, size.height - stroke)
        drawArc(SafeTrackColors.Track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
        drawArc(
            color = color.copy(alpha = if (low) 0.5f + 0.5f * pulse else 1f),
            startAngle = -90f,
            sweepAngle = 360f * fill.value,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arc,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}
