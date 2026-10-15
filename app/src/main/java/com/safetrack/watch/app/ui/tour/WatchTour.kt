package com.safetrack.watch.app.ui.tour

import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import com.safetrack.watch.app.theme.SafeTrackColors

/** Whether this watch has shown (or skipped) the tour; replayable from Status. */
object WatchTourPrefs {
    private const val FILE = "safetrack_watch_tour"
    private const val KEY = "seen"

    fun seen(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun markSeen(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
    }
}

private data class TourPage(val title: String, val body: String, val art: @Composable () -> Unit)

/**
 * The first-run tour on the watch: four round pages, swipe to move, Skip on
 * every page but the last. Shown after the first pairing, before the
 * location-permission screens.
 */
@Composable
fun WatchTour(onDone: () -> Unit) {
    val pages = listOf(
        TourPage("Hold for SOS", "Hold SOS until the ring fills, or shake the watch.") { HoldArt() },
        TourPage("You can cancel", "A 3-second countdown follows. Tap CANCEL if it was a mistake.") { CountdownArt() },
        TourPage("Keep it ready", "Keep the watch charged and connected so your guardian gets updates.") { BatteryArt() },
        TourPage("Allow location", "Choose \"Allow all the time\" next, so sharing keeps working after a restart.") { PinArt() },
    )
    val pager = rememberPagerState(pageCount = { pages.size })

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->
            val page = pages[index]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 26.dp)
                    .semantics { contentDescription = "Tour page ${index + 1} of ${pages.size}. ${page.title}. ${page.body}" },
            ) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) { page.art() }
                Spacer(Modifier.height(8.dp))
                Text(
                    page.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SafeTrackColors.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    page.body,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = SafeTrackColors.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(10.dp))
                if (index == pages.lastIndex) {
                    Button(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SafeTrackColors.Green, contentColor = Color.Black),
                    ) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("CONTINUE", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                } else {
                    Text(
                        "SKIP",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = SafeTrackColors.Green,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClickLabel = "Skip the tour", onClick = onDone)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
            }
        }

        // Page dots along the bottom edge; the current one is a longer pill.
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
        ) {
            pages.indices.forEach { index ->
                Box(
                    Modifier
                        .height(5.dp)
                        .width(if (index == pager.currentPage) 16.dp else 5.dp)
                        .clip(CircleShape)
                        .background(if (index == pager.currentPage) SafeTrackColors.Green else SafeTrackColors.Divider),
                )
            }
        }
    }
}

@Composable
private fun pulse(): Float {
    val value by rememberInfiniteTransition(label = "tour").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
        label = "tour-progress",
    )
    return value
}

@Composable
private fun HoldArt() {
    val progress = pulse()
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 5.dp.toPx()
        drawArc(SafeTrackColors.DangerDeep, 0f, 360f, false, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
        drawArc(SafeTrackColors.Danger, -90f, 360f * progress, false, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
        drawCircle(SafeTrackColors.Danger, radius = size.minDimension / 2 - 11.dp.toPx())
    }
    Text("SOS", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
}

@Composable
private fun CountdownArt() {
    val progress = pulse()
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 5.dp.toPx()
        drawArc(SafeTrackColors.DangerDeep, 0f, 360f, false, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
        drawArc(SafeTrackColors.Danger, -90f, 360f * (1f - progress), false, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
    }
    Text((3 - (pulse() * 3).toInt().coerceAtMost(2)).toString(), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = SafeTrackColors.TextPrimary)
}

@Composable
private fun BatteryArt() {
    val progress = pulse()
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 5.dp.toPx()
        drawArc(SafeTrackColors.SurfaceAccent, 0f, 360f, false, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
        drawArc(SafeTrackColors.Green, -90f, 360f * (0.4f + 0.6f * progress), false, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
    }
    // A small battery, drawn in the middle of the ring.
    Canvas(Modifier.size(width = 26.dp, height = 14.dp)) {
        val stroke = 2.dp.toPx()
        drawRoundRect(
            SafeTrackColors.Green,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - 4.dp.toPx(), size.height - stroke),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            style = Stroke(stroke),
        )
        drawRect(SafeTrackColors.Green, topLeft = Offset(size.width - 3.dp.toPx(), size.height * 0.3f), size = Size(3.dp.toPx(), size.height * 0.4f))
        drawRect(SafeTrackColors.Green, topLeft = Offset(4.dp.toPx(), 4.dp.toPx()), size = Size((size.width - 12.dp.toPx()) * (0.4f + 0.6f * progress), size.height - 8.dp.toPx()))
    }
}

@Composable
private fun PinArt() {
    val progress = pulse()
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(SafeTrackColors.Green.copy(alpha = 0.35f * (1f - progress)), radius = size.minDimension / 2 * (0.5f + 0.5f * progress))
        drawCircle(SafeTrackColors.Green, radius = 12.dp.toPx())
        drawCircle(Color.Black, radius = 5.dp.toPx())
    }
}
