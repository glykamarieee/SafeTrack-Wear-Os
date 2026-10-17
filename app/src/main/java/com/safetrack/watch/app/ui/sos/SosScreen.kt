package com.safetrack.watch.app.ui.sos

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.safetrack.watch.app.theme.LocalReduceMotion
import com.safetrack.watch.app.theme.SafeTrackColors
import com.safetrack.watch.app.ui.components.PillButton
import com.safetrack.watch.app.ui.components.PillStyle
import com.safetrack.watch.app.ui.components.WatchSounds
import com.safetrack.watch.app.ui.components.rememberPulse
import com.safetrack.watch.app.ui.components.roundInset
import com.safetrack.watch.domain.model.SosState
import com.safetrack.watch.services.sos.SosController

/**
 * Full-screen SOS flow, drawn over everything else whenever an SOS is active.
 * Cancellation and sending behaviour are unchanged; only the look is new.
 */
@Composable
fun SosScreen(state: SosState, onCancel: () -> Unit, onDismiss: () -> Unit) {
    BackHandler(enabled = state !is SosState.Idle) {
        when (state) {
            is SosState.Countdown -> onCancel()
            is SosState.Sending -> Unit
            else -> onDismiss()
        }
    }

    if (state is SosState.Idle) return

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Final).changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        SosContent(state, onCancel, onDismiss)
    }
}

@Composable
private fun SosContent(state: SosState, onCancel: () -> Unit, onDismiss: () -> Unit) {
    // One sound per result (the countdown ticks are in Countdown).
    LaunchedEffect(state::class) {
        when (state) {
            SosState.Cancelled -> WatchSounds.play(WatchSounds.Effect.Cancel)
            is SosState.Sent -> WatchSounds.play(WatchSounds.Effect.Sent)
            is SosState.Pending -> WatchSounds.play(WatchSounds.Effect.Error)
            else -> Unit
        }
    }
    when (state) {
        SosState.Idle -> Unit
        is SosState.Countdown -> Countdown(state.secondsLeft, onCancel)
        SosState.Cancelled -> Message(
            icon = Icons.Rounded.Close,
            accent = SafeTrackColors.Neutral,
            soft = SafeTrackColors.NeutralSoft,
            title = "SOS cancelled",
            lines = listOf("No alert was sent.", "Back to your watch."),
        )
        SosState.Sending -> Sending()
        is SosState.Sent -> Message(
            icon = Icons.Rounded.CheckCircle,
            accent = SafeTrackColors.Green,
            soft = SafeTrackColors.Mint,
            title = "SOS sent",
            lines = listOf(
                "Your guardian is being notified.",
                if (state.locationIncluded) "Your latest location was added." else "Location was unavailable.",
            ),
            celebrate = true,
            onOk = onDismiss,
        )
        is SosState.Pending -> Message(
            icon = Icons.Rounded.Refresh,
            accent = SafeTrackColors.Warning,
            soft = SafeTrackColors.WarningSoft,
            title = "SOS waiting",
            lines = listOf("No connection right now.", "It sends when you're back online."),
            onOk = onDismiss,
        )
    }
}

/** Red, urgent background shared by the countdown and sending screens. */
private val urgentBackground = Brush.radialGradient(listOf(SafeTrackColors.DangerBright, SafeTrackColors.Danger, SafeTrackColors.DangerDeep))

@Composable
private fun Countdown(secondsLeft: Int, onCancel: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val haptics = LocalHapticFeedback.current
    // The edge ring empties over the whole countdown, in step with the seconds.
    val remaining = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        remaining.animateTo(0f, tween(SosController.COUNTDOWN_SECONDS * 1_000, easing = LinearEasing))
    }
    // A tick on every second.
    LaunchedEffect(secondsLeft) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        WatchSounds.play(WatchSounds.Effect.Tick)
    }
    val cancelPulse = rememberPulse(700, still = 0f)

    // No time text over the urgent red screen.
    ScreenScaffold(timeText = {}) {
    Box(
        Modifier
            .fillMaxSize()
            .background(urgentBackground),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(3.dp)) {
            val stroke = 7.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Color.White.copy(alpha = 0.22f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(
                color = Color.White,
                startAngle = -90f,
                sweepAngle = 360f * remaining.value,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        // The countdown is a fixed graphic sized for the circle: very large
        // font settings are capped so nothing is clipped.
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.1f))) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.roundInset(0.14f),
        ) {
            Text(
                "SENDING SOS IN",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                color = Color.White,
                maxLines = 1,
            )
            AnimatedContent(
                targetState = secondsLeft,
                transitionSpec = {
                    if (reduce) {
                        fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                    } else {
                        (scaleIn(tween(260), initialScale = 1.6f) + fadeIn(tween(200))) togetherWith
                            (scaleOut(tween(200), targetScale = 0.6f) + fadeOut(tween(160)))
                    }
                },
                label = "countdown",
            ) { seconds ->
                Text(
                    seconds.toString(),
                    fontSize = 64.sp,
                    lineHeight = 68.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
            Spacer(Modifier.height(4.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(118.dp)
                    .heightIn(min = 48.dp)
                    .graphicsLayer {
                        val s = 1f + 0.04f * cancelPulse
                        scaleX = s
                        scaleY = s
                    }
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .clickable(role = Role.Button, onClickLabel = "Cancel SOS") {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCancel()
                    },
            ) {
                Text("CANCEL", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, color = SafeTrackColors.Danger, maxLines = 1)
            }
        }
        }
    }
    }
}

@Composable
private fun Sending() {
    val haptics = LocalHapticFeedback.current
    val reduce = LocalReduceMotion.current
    // The SOS is confirmed: one strong buzz.
    LaunchedEffect(Unit) { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    val spin by rememberInfiniteTransition(label = "sending").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "sending-spin",
    )

    ScreenScaffold(timeText = {}) {
    Box(
        Modifier
            .fillMaxSize()
            .background(urgentBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.roundInset(0.14f)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 5.dp.toPx()
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(Color.White.copy(alpha = 0.25f), 0f, 360f, false, Offset(stroke / 2, stroke / 2), arcSize, style = Stroke(stroke))
                    drawArc(Color.White, if (reduce) -90f else spin - 90f, 100f, false, Offset(stroke / 2, stroke / 2), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Text("SOS", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
            Spacer(Modifier.height(8.dp))
            Text("SENDING SOS", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
            Text(
                "Adding your latest location",
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
    }
}

@Composable
private fun Message(
    icon: ImageVector,
    accent: Color,
    soft: Color,
    title: String,
    lines: List<String>,
    celebrate: Boolean = false,
    onOk: (() -> Unit)? = null,
) {
    val reduce = LocalReduceMotion.current
    val pop = remember { Animatable(if (reduce) 1f else 0.4f) }
    val ring = remember { Animatable(if (reduce || !celebrate) 1f else 0f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(Unit) { if (ring.value < 1f) ring.animateTo(1f, tween(900)) }

    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    Box(
        Modifier
            .fillMaxSize()
            .background(SafeTrackColors.Background),
    ) {
        ScreenScaffold(scrollState = listState) { contentPadding ->
            ScalingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                item {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(58.dp)) {
                        Canvas(Modifier.fillMaxSize()) {
                            if (ring.value < 1f) {
                                drawCircle(
                                    color = SafeTrackColors.GreenBright.copy(alpha = 0.5f * (1f - ring.value)),
                                    radius = size.minDimension / 2 * (0.6f + 0.4f * ring.value),
                                    style = Stroke(3.dp.toPx()),
                                )
                            }
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(46.dp)
                                .graphicsLayer {
                                    scaleX = pop.value
                                    scaleY = pop.value
                                }
                                .clip(CircleShape)
                                .background(soft),
                        ) {
                            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
                        }
                    }
                }
                item {
                    Text(
                        title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SafeTrackColors.Forest,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.roundInset(0.06f),
                    )
                }
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.roundInset(0.06f)) {
                        lines.forEachIndexed { index, line ->
                            Text(
                                line,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = if (index == 0) SafeTrackColors.TextMuted else SafeTrackColors.TextFaint,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                if (onOk != null) {
                    item {
                        PillButton(label = "DONE", onClick = onOk, style = PillStyle.Primary, modifier = Modifier.padding(top = 4.dp).width(110.dp))
                    }
                }
            }
        }
    }
}
