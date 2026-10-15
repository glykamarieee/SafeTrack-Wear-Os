package com.safetrack.watch.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.timeTextCurvedText
import androidx.wear.compose.material3.timeTextSeparator
import com.safetrack.watch.R
import com.safetrack.watch.app.theme.LocalReduceMotion
import com.safetrack.watch.app.theme.SafeTrackColors
import com.safetrack.watch.domain.model.ConnectionState
import com.safetrack.watch.domain.model.SafeZoneStatus
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------
// Round-screen sizing
// ---------------------------------------------------------------------------

/** The watch's width in dp (206 dp on a Galaxy Watch8 40 mm). */
@Composable
fun screenWidthDp(): Dp = LocalConfiguration.current.screenWidthDp.dp

/**
 * Side padding as a share of the screen width, so content stays inside the
 * circle on every watch size. Wear OS guidance: about 5% extra for list items
 * (on top of the scaffold's padding), 12-15% for content not in a list.
 */
@Composable
fun Modifier.roundInset(fraction: Float): Modifier = padding(horizontal = screenWidthDp() * fraction)

// ---------------------------------------------------------------------------
// Motion
// ---------------------------------------------------------------------------

/**
 * Fades, lifts and scales an element in, staggered by [index]. Plays once per
 * item: rememberSaveable keeps the "done" flag when a list item scrolls away
 * and back. Instant when animations are turned off.
 */
@Composable
fun Modifier.entrance(index: Int): Modifier {
    val reduce = LocalReduceMotion.current
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (played || reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(index * 70L)
            progress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        }
        played = true
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p
        val scale = 0.9f + 0.1f * p
        scaleX = scale
        scaleY = scale
        translationY = (1f - p) * 14.dp.toPx()
    }
}

/**
 * A slow 0..1..0 wave for breathing glows and blinking dots. Loops only run
 * while the screen is on (Compose stops frames when the app is hidden).
 * Returns [still] when animations are off or [enabled] is false.
 */
@Composable
fun rememberPulse(periodMs: Int, enabled: Boolean = true, still: Float = 0.5f): Float {
    if (!enabled || LocalReduceMotion.current) return still
    val value by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse-value",
    )
    return value
}

/** A 0..1 ramp that restarts every [periodMs], for ripples. */
@Composable
fun rememberRipple(periodMs: Int, enabled: Boolean = true): Float {
    if (!enabled || LocalReduceMotion.current) return 0f
    val value by rememberInfiniteTransition(label = "ripple").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs), RepeatMode.Restart),
        label = "ripple-value",
    )
    return value
}

// ---------------------------------------------------------------------------
// Brand
// ---------------------------------------------------------------------------

/** The SafeTrack logo, tinted (the bundled file is white). */
@Composable
fun SafeTrackBrand(compact: Boolean = false, tint: Color = SafeTrackColors.Green) {
    Image(
        painter = painterResource(R.drawable.safetrack_logo),
        contentDescription = "SafeTrack",
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(tint),
        modifier = if (compact) Modifier.size(width = 64.dp, height = 30.dp) else Modifier.size(width = 84.dp, height = 40.dp),
    )
}

/** SafeTrack's friendly mascot: a heart in a soft mint bubble that gently bobs. */
@Composable
fun Mascot(size: Dp = 30.dp, color: Color = SafeTrackColors.Green) {
    val bob = rememberPulse(1800)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .graphicsLayer { translationY = (bob - 0.5f) * 3.dp.toPx() }
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color.White, SafeTrackColors.MintDeep)))
            .border(1.dp, color.copy(alpha = 0.25f), CircleShape),
    ) {
        Icon(
            Icons.Rounded.Favorite,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(size * 0.55f)
                .graphicsLayer {
                    val beat = 0.94f + 0.08f * bob
                    scaleX = beat
                    scaleY = beat
                },
        )
    }
}

/**
 * The time curved along the top edge, optionally with the battery
 * ("12:48 · 54%"). A solid light background keeps it crisp over scrolling
 * content.
 */
@Composable
fun SafeTrackTimeText(batteryPercent: Int? = null) {
    val style = TimeTextDefaults.timeTextStyle(color = SafeTrackColors.Forest)
    TimeText(backgroundColor = SafeTrackColors.Background) { time ->
        timeTextCurvedText(time, style)
        if (batteryPercent != null) {
            timeTextSeparator(style)
            timeTextCurvedText("$batteryPercent%", style)
        }
    }
}

// ---------------------------------------------------------------------------
// Text
// ---------------------------------------------------------------------------

@Composable
fun Eyebrow(text: String, color: Color = SafeTrackColors.Green) {
    Text(
        text = text.uppercase(),
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun ScreenTitle(text: String, color: Color = SafeTrackColors.Forest) {
    Text(
        text = text,
        color = color,
        fontSize = 16.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun BodyText(text: String, color: Color = SafeTrackColors.TextMuted, maxLines: Int = 3) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        textAlign = TextAlign.Center,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---------------------------------------------------------------------------
// Surfaces and controls
// ---------------------------------------------------------------------------

/** A soft rounded card; the large radius follows the round screen. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    containerColor: Color = SafeTrackColors.Surface,
    borderColor: Color = SafeTrackColors.Divider,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .then(
                if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick) else Modifier,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        content()
    }
}

enum class PillStyle { Primary, Tonal, Outline }

/** Full-width rounded button, at least 48 dp tall. */
@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val (background, content, border) = when (style) {
        PillStyle.Primary -> Triple(SafeTrackColors.Green, Color.White, Color.Transparent)
        PillStyle.Tonal -> Triple(SafeTrackColors.Mint, SafeTrackColors.Forest, Color.Transparent)
        PillStyle.Outline -> Triple(SafeTrackColors.Surface, SafeTrackColors.Green, SafeTrackColors.Green)
    }
    val shape = RoundedCornerShape(50)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(shape)
            .background(
                if (style == PillStyle.Primary) {
                    Brush.verticalGradient(listOf(SafeTrackColors.GreenBright, background))
                } else {
                    Brush.verticalGradient(listOf(background, background))
                },
            )
            .border(if (border == Color.Transparent) 0.dp else 1.5.dp, border, shape)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp))
        } else {
            Text(
                label,
                color = content,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.6.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A dot that blinks softly when [blinking]. */
@Composable
fun StatusDot(color: Color, size: Dp = 8.dp, blinking: Boolean = false) {
    val wave = rememberPulse(700, enabled = blinking, still = 1f)
    Box(
        Modifier
            .size(size)
            .graphicsLayer { alpha = if (blinking) 0.35f + 0.65f * wave else 1f }
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun StatusPill(label: String, color: Color, modifier: Modifier = Modifier, blinking: Boolean = false) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color, size = 7.dp, blinking = blinking)
        Spacer(Modifier.width(5.dp))
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A small label above its value, centred. Stacked rather than side by side so
 * long values never get cut, even with the watch's largest font.
 */
@Composable
fun StatusRow(label: String, value: String, dotColor: Color? = null) {
    androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, color = SafeTrackColors.TextFaint, fontSize = 10.sp, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dotColor != null) {
                StatusDot(dotColor, size = 7.dp)
                Spacer(Modifier.width(4.dp))
            }
            Text(
                value,
                color = SafeTrackColors.Forest,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SoftDivider() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(SafeTrackColors.Divider),
    )
}

// ---------------------------------------------------------------------------
// Wording and colours for states
// ---------------------------------------------------------------------------

/**
 * How a safe-zone status looks and reads on the child's watch. [color] is for
 * text and icons (passes contrast); [ring] is the brighter decorative colour.
 */
data class ZoneLook(val color: Color, val ring: Color, val soft: Color, val title: String, val detail: String)

fun zoneLook(status: SafeZoneStatus): ZoneLook = when (status) {
    is SafeZoneStatus.Inside -> ZoneLook(SafeTrackColors.Safe, SafeTrackColors.GreenBright, SafeTrackColors.Mint, "In a safe place", status.zoneName)
    SafeZoneStatus.Outside -> ZoneLook(SafeTrackColors.Warning, SafeTrackColors.WarningFill, SafeTrackColors.WarningSoft, "Not in a safe place", "Your guardian sees your latest location")
    is SafeZoneStatus.Unavailable -> when (status.reason) {
        SafeZoneStatus.Reason.NO_SAFE_ZONE -> ZoneLook(SafeTrackColors.Neutral, SafeTrackColors.MintDeep, SafeTrackColors.NeutralSoft, "No safe place yet", "Your guardian can add one")
        SafeZoneStatus.Reason.NO_LOCATION -> ZoneLook(SafeTrackColors.Neutral, SafeTrackColors.MintDeep, SafeTrackColors.NeutralSoft, "Finding your place", "Waiting for location")
        SafeZoneStatus.Reason.NOT_CHECKED -> ZoneLook(SafeTrackColors.Neutral, SafeTrackColors.MintDeep, SafeTrackColors.NeutralSoft, "Checking your place", "Using your latest location")
    }
}

fun connectionLook(state: ConnectionState): Pair<Color, String> = when (state) {
    ConnectionState.CONNECTED -> SafeTrackColors.Safe to "Connected"
    ConnectionState.CONNECTING -> SafeTrackColors.Warning to "Connecting"
    ConnectionState.DISCONNECTED -> SafeTrackColors.Neutral to "Offline"
}

/** The first word of a name, for a friendly "Hi, Mia!". */
fun firstName(name: String): String = name.trim().split(Regex("\\s+")).firstOrNull().orEmpty()

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
