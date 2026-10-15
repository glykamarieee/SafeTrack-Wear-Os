package com.safetrack.watch.app.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.safetrack.watch.app.theme.SafeTrackColors
import kotlinx.coroutines.launch

/**
 * Large round SOS control. Existing behavior is unchanged: hold for 1.5 seconds
 * to open the separate cancellable SOS confirmation countdown.
 */
@Composable
fun SosHoldButton(onHoldComplete: () -> Unit, size: Dp = 124.dp) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var holding by remember { mutableStateOf(false) }
    val onComplete by rememberUpdatedState(onHoldComplete)

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
                            scope.launch { progress.animateTo(0f, tween(180)) }
                        }
                    },
                )
            },
    ) {
        // A soft breathing glow while waiting for a hold.
        val glow by rememberInfiniteTransition(label = "sos-glow").animateFloat(
            initialValue = 0.10f,
            targetValue = 0.28f,
            animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
            label = "sos-glow-alpha",
        )

        Canvas(Modifier.fillMaxSize()) {
            val ringStroke = 7.dp.toPx()
            val inset = ringStroke / 2 + 1.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)

            if (!holding) {
                drawCircle(color = SafeTrackColors.Danger.copy(alpha = glow), radius = this.size.minDimension / 2)
            }
            // Track, then the hold progress: fills clockwise over HOLD_MS.
            drawArc(
                color = SafeTrackColors.DangerDeep,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(ringStroke),
            )
            drawArc(
                color = SafeTrackColors.Danger,
                startAngle = -90f,
                sweepAngle = 360f * progress.value,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(ringStroke, cap = StrokeCap.Round),
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(14.dp)
                .fillMaxSize()
                .scale(if (holding) 0.95f else 1f)
                .clip(CircleShape)
                .background(SafeTrackColors.Danger),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "SOS",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black,
                )
                Text(
                    if (holding) "KEEP HOLDING" else "HOLD",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = Color.Black.copy(alpha = 0.82f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private const val HOLD_MS = 1_500
