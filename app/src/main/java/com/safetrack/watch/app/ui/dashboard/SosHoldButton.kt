package com.safetrack.watch.app.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.safetrack.watch.app.theme.SafeTrackColors
import com.safetrack.watch.app.ui.components.rememberPulse
import kotlinx.coroutines.launch

/**
 * The big round SOS control, the "heart" of the dashboard. Behaviour is
 * unchanged: hold for 1.5 seconds to open the separate, cancellable SOS
 * countdown.
 *
 * - Idle: a slow breathing glow.
 * - Holding: the button presses in, a ring fills in exactly [HOLD_MS] and the
 *   watch ticks at the start and when the ring completes.
 * - Released early: the ring and the button spring back.
 */
@Composable
fun SosHoldButton(onHoldComplete: () -> Unit, size: Dp = 118.dp) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var holding by remember { mutableStateOf(false) }
    val onComplete by rememberUpdatedState(onHoldComplete)

    val press by animateFloatAsState(
        targetValue = if (holding) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "sos-press",
    )
    val breath = rememberPulse(1600, enabled = !holding)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .semantics {
                role = Role.Button
                contentDescription = "SOS. Press and hold to begin the emergency confirmation countdown."
                onLongClick(label = "Begin SOS") {
                    onComplete()
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        holding = true
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val fill = scope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onComplete()
                            progress.snapTo(0f)
                        }
                        tryAwaitRelease()
                        holding = false
                        if (fill.isActive) {
                            fill.cancel()
                            scope.launch {
                                progress.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = this.size.minDimension / 2
            // Breathing glow behind the button.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(SafeTrackColors.DangerBright.copy(alpha = 0.10f + 0.22f * breath), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
            )
            // Hold ring: a soft track, then the fill that completes in HOLD_MS.
            val stroke = 6.dp.toPx()
            val inset = stroke / 2 + 2.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            // The track appears only while holding, so the idle button stays clean.
            if (holding || progress.value > 0f) {
                drawArc(
                    color = SafeTrackColors.DangerSoft,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke),
                )
                drawArc(
                    color = SafeTrackColors.Danger,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.value,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(13.dp)
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = press
                    scaleY = press
                    shadowElevation = if (holding) 2.dp.toPx() else 8.dp.toPx()
                    shape = CircleShape
                    clip = true
                    ambientShadowColor = SafeTrackColors.Danger
                    spotShadowColor = SafeTrackColors.Danger
                }
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(SafeTrackColors.DangerBright, SafeTrackColors.Danger))),
        ) {
            // The label is part of the button's graphic: cap very large font
            // scales so "KEEP HOLDING" never spills out of the circle.
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.1f))) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "SOS",
                    fontSize = 28.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = SafeTrackColors.OnDanger,
                )
                Text(
                    if (holding) "KEEP HOLDING" else "HOLD",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = SafeTrackColors.OnDanger,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
            }
        }
    }
}

private const val HOLD_MS = 1_500
