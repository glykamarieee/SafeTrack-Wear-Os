package com.safetrack.watch.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.safetrack.watch.app.theme.SafeTrackColors
import com.safetrack.watch.domain.model.ConnectionState
import com.safetrack.watch.domain.model.SafeZoneStatus

@Composable
fun StatusDot(color: Color, size: Int = 12) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun ScreenTitle(text: String, color: Color = SafeTrackColors.GreenLight) {
    Text(
        text = text,
        color = color,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Rounded dark card; clickable when [onClick] is given. */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        content()
    }
}

/** "Label ........ value" line used for connection, battery and network. */
@Composable
fun StatusRow(label: String, value: String, dotColor: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = SafeTrackColors.TextMuted, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dotColor != null) {
                StatusDot(dotColor, size = 9)
                Spacer(Modifier.width(5.dp))
            }
            Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SafeZoneBadge(status: SafeZoneStatus, large: Boolean = false) {
    val (color, label) = safeZoneLook(status)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("SAFE-ZONE STATUS", color = SafeTrackColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.size(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color, size = if (large) 16 else 13)
            Spacer(Modifier.width(7.dp))
            Text(
                label,
                color = color,
                fontSize = if (large) 18.sp else 16.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

fun safeZoneLook(status: SafeZoneStatus): Pair<Color, String> = when (status) {
    is SafeZoneStatus.Inside -> SafeTrackColors.Safe to "INSIDE SAFE ZONE"
    SafeZoneStatus.Outside -> SafeTrackColors.Danger to "OUTSIDE SAFE ZONE"
    is SafeZoneStatus.Unavailable -> SafeTrackColors.Neutral to "UNAVAILABLE"
}

fun connectionLook(state: ConnectionState): Pair<Color, String> = when (state) {
    ConnectionState.CONNECTED -> SafeTrackColors.Safe to "Connected"
    ConnectionState.CONNECTING -> SafeTrackColors.Warning to "Connecting"
    ConnectionState.DISCONNECTED -> SafeTrackColors.Danger to "Disconnected"
}

/** "Just now", "5 min ago", "2 h ago", "3 days ago"; null time -> [never]. */
fun timeAgo(epochMillis: Long?, now: Long = System.currentTimeMillis(), never: String = "Not yet"): String {
    if (epochMillis == null) return never
    val minutes = ((now - epochMillis).coerceAtLeast(0)) / 60_000
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        else -> "${minutes / (24 * 60)} days ago"
    }
}
