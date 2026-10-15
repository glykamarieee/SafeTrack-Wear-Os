package com.safetrack.watch.app.ui.sos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.safetrack.watch.app.theme.SafeTrackColors
import com.safetrack.watch.app.ui.components.Eyebrow
import com.safetrack.watch.domain.model.SosState

/**
 * Full-screen SOS flow, drawn over everything else whenever an SOS is active.
 * Existing cancellation/sending behavior remains unchanged.
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
    when (state) {
        SosState.Idle -> Unit
        is SosState.Countdown -> Countdown(state.secondsLeft, onCancel)
        SosState.Cancelled -> Message(
            background = SafeTrackColors.Background,
            title = "SOS cancelled",
            titleColor = SafeTrackColors.TextPrimary,
            lines = listOf("No emergency alert was sent.", "Returning to monitoring."),
        )
        SosState.Sending -> Sending()
        is SosState.Sent -> Message(
            background = Color(0xFF071711),
            title = "SOS sent",
            titleColor = SafeTrackColors.Safe,
            icon = {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = SafeTrackColors.Safe,
                    modifier = Modifier.size(40.dp),
                )
            },
            lines = listOf(
                "Your guardian is being notified.",
                if (state.locationIncluded) "Latest available location included." else "Location was unavailable.",
            ),
            onOk = onDismiss,
        )
        is SosState.Pending -> Message(
            background = Color(0xFF1C1608),
            title = "SOS waiting to send",
            titleColor = SafeTrackColors.Warning,
            icon = {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = SafeTrackColors.Warning,
                    modifier = Modifier.size(37.dp),
                )
            },
            lines = listOf(
                "No connection is available right now.",
                "SafeTrack will retry when communication returns.",
            ),
            onOk = onDismiss,
        )
    }
}

@Composable
private fun Countdown(secondsLeft: Int, onCancel: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF3F0D13)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 20.dp),
        ) {
            Eyebrow("Emergency confirmation", color = Color.White.copy(alpha = 0.70f))
            Text(
                "SOS in",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(2.dp, SafeTrackColors.Danger.copy(alpha = 0.72f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    secondsLeft.toString(),
                    fontSize = 50.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
            Text(
                "Cancel now if this was accidental.",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.76f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            Button(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF5C1119),
                ),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("CANCEL SOS", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun Sending() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF3F0D13)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text("SENDING SOS", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text(
                "Preparing the latest available watch location",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.76f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun Message(
    background: Color,
    title: String,
    titleColor: Color,
    lines: List<String>,
    icon: (@Composable () -> Unit)? = null,
    onOk: (() -> Unit)? = null,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 23.dp),
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(titleColor.copy(alpha = 0.11f)),
                    contentAlignment = Alignment.Center,
                ) {
                    icon()
                }
                Spacer(Modifier.height(7.dp))
            }
            Text(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = titleColor,
                textAlign = TextAlign.Center,
            )
            lines.forEachIndexed { index, line ->
                Text(
                    line,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = if (index == 0) SafeTrackColors.TextPrimary else SafeTrackColors.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = if (index == 0) 5.dp else 2.dp),
                )
            }
            if (onOk != null) {
                Spacer(Modifier.height(9.dp))
                Button(
                    onClick = onOk,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                ) {
                    Text("DONE", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}
