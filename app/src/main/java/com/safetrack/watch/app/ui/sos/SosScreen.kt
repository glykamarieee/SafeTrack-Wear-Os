package com.safetrack.watch.app.ui.sos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.safetrack.watch.domain.model.SosState

/**
 * Full-screen SOS flow, drawn over everything else whenever an SOS is active.
 * Back/swipe is blocked during the countdown so only CANCEL cancels.
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

    // Opaque to touches, so nothing underneath can be tapped or swiped.
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Final).changes.forEach { it.consume() }
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
            background = Color(0xFF1C1C1C),
            title = "SOS CANCELLED",
            titleColor = Color.White,
            lines = listOf("Returning to monitoring…"),
        )
        SosState.Sending -> Sending()
        is SosState.Sent -> Message(
            background = Color(0xFF0E3B25),
            title = "SOS SENT",
            titleColor = SafeTrackColors.Safe,
            icon = { Icon(Icons.Filled.CheckCircle, null, tint = SafeTrackColors.Safe, modifier = Modifier.size(34.dp)) },
            lines = listOf(
                "Emergency alert sent.",
                if (state.locationIncluded) "Location included." else "Location unavailable.",
                "Your guardian is being notified.",
            ),
            onOk = onDismiss,
        )
        is SosState.Pending -> Message(
            background = Color(0xFF3A2C05),
            title = "SOS PENDING",
            titleColor = SafeTrackColors.Warning,
            icon = { Icon(Icons.Filled.Warning, null, tint = SafeTrackColors.Warning, modifier = Modifier.size(30.dp)) },
            lines = listOf(
                "No connection.",
                "Your emergency alert will be sent when communication is available.",
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
            .background(SafeTrackColors.DangerDeep),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 20.dp)) {
            Text("SOS ACTIVATION", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Sending SOS in…", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
            Text(
                secondsLeft.toString(),
                fontSize = 58.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
            Button(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = SafeTrackColors.DangerDeep),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("CANCEL SOS", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
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
            .background(SafeTrackColors.DangerDeep),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(10.dp))
            Text("SENDING SOS…", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("Adding your location", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
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
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            icon?.invoke()
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = titleColor)
            lines.forEach {
                Text(it, fontSize = 13.sp, color = Color.White, textAlign = TextAlign.Center)
            }
            if (onOk != null) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onOk,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.height(44.dp),
                ) {
                    Text("OK", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp))
                }
            }
        }
    }
}
