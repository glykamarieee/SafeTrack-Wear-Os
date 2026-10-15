package com.safetrack.watch.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.safetrack.watch.R
import com.safetrack.watch.app.theme.SafeTrackColors
import com.safetrack.watch.domain.model.ConnectionState
import com.safetrack.watch.domain.model.SafeZoneStatus

@Composable
fun SafeTrackBrand(compact: Boolean = false) {
    Image(
        painter = painterResource(R.drawable.safetrack_logo),
        contentDescription = "SafeTrack",
        contentScale = ContentScale.Fit,
        modifier = if (compact) {
            Modifier.size(width = 92.dp, height = 38.dp)
        } else {
            Modifier.size(width = 128.dp, height = 56.dp)
        },
    )
}

@Composable
fun StatusDot(color: Color, size: Int = 10) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun Eyebrow(text: String, color: Color = SafeTrackColors.GreenLight) {
    Text(
        text = text.uppercase(),
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun ScreenTitle(text: String, color: Color = SafeTrackColors.TextPrimary) {
    Text(
        text = text,
        color = color,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Selective grouped surface for information that belongs together. */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor)
            .border(1.dp, SafeTrackColors.Divider.copy(alpha = 0.72f), RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        content()
    }
}

@Composable
fun StatusPill(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.30f), RoundedCornerShape(99.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color, size = 7)
        Spacer(Modifier.width(5.dp))
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/** Compact row used for connection, battery, network and location status. */
@Composable
fun StatusRow(label: String, value: String, dotColor: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = SafeTrackColors.TextMuted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dotColor != null) {
                StatusDot(dotColor, size = 8)
                Spacer(Modifier.width(5.dp))
            }
            Text(value, color = SafeTrackColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SoftDivider() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(SafeTrackColors.Divider.copy(alpha = 0.70f)),
    )
}

@Composable
fun SafeZoneBadge(status: SafeZoneStatus, large: Boolean = false) {
    val (color, label) = safeZoneLook(status)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Eyebrow("Safe-zone status", color = SafeTrackColors.TextMuted)
        Spacer(Modifier.height(if (large) 8.dp else 5.dp))
        Box(
            modifier = Modifier
                .size(if (large) 54.dp else 42.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.13f))
                .border(1.dp, color.copy(alpha = 0.38f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            StatusDot(color, size = if (large) 16 else 13)
        }
        Spacer(Modifier.height(if (large) 7.dp else 5.dp))
        Text(
            label,
            color = color,
            fontSize = if (large) 18.sp else 15.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
    }
}

fun safeZoneLook(status: SafeZoneStatus): Pair<Color, String> = when (status) {
    is SafeZoneStatus.Inside -> SafeTrackColors.Safe to "INSIDE SAFE ZONE"
    SafeZoneStatus.Outside -> SafeTrackColors.Danger to "OUTSIDE SAFE ZONE"
    is SafeZoneStatus.Unavailable -> SafeTrackColors.Neutral to "STATUS UNAVAILABLE"
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
