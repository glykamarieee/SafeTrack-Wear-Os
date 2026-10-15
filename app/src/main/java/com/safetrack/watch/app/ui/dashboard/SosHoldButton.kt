package com.safetrack.watch.app.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
        Canvas(Modifier.fillMaxSize()) {
            val outerStroke = 2.dp.toPx()
            val progressStroke = 7.dp.toPx()
            val outerInset = outerStroke / 2
            val progressInset = 8.dp.toPx()

            drawArc(
                color = SafeTrackColors.Danger.copy(alpha = 0.20f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(outerInset, outerInset),
                size = Size(this.size.width - outerStroke, this.size.height - outerStroke),
                style = Stroke(outerStroke),
            )
            drawArc(
                color = Color.White.copy(alpha = 0.14f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(progressInset, progressInset),
                size = Size(this.size.width - progressInset * 2, this.size.height - progressInset * 2),
                style = Stroke(progressStroke),
            )
            drawArc(
                color = Color.White,
                startAngle = -90f,
                sweepAngle = 360f * progress.value,
                useCenter = false,
                topLeft = Offset(progressInset, progressInset),
                size = Size(this.size.width - progressInset * 2, this.size.height - progressInset * 2),
                style = Stroke(progressStroke, cap = StrokeCap.Round),
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(17.dp)
                .fillMaxSize()
                .clip(CircleShape)
                .background(if (holding) SafeTrackColors.DangerDeep else Color(0xFF5D171F))
                .border(
                    1.dp,
                    if (holding) Color.White.copy(alpha = 0.30f) else SafeTrackColors.Danger.copy(alpha = 0.42f),
                    CircleShape,
                ),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "SOS",
                    fontSize = 31.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
                Text(
                    if (holding) "KEEP HOLDING" else "PRESS + HOLD",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White.copy(alpha = 0.92f),
                    textAlign = TextAlign.Center,
                )
                if (!holding) {
                    Text(
                        "1.5 sec",
                        fontSize = 8.sp,
                        color = Color.White.copy(alpha = 0.62f),
                    )
                }
            }
        }
    }
}

private const val HOLD_MS = 1_500
