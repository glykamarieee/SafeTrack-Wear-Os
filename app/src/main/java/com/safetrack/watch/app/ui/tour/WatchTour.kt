package com.safetrack.watch.app.ui.tour

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.safetrack.watch.app.theme.SafeTrackColors
import com.safetrack.watch.app.ui.components.BodyText
import com.safetrack.watch.app.ui.components.PillButton
import com.safetrack.watch.app.ui.components.ScreenTitle
import com.safetrack.watch.app.ui.components.rememberRipple
import com.safetrack.watch.app.ui.components.roundInset
import kotlin.math.absoluteValue

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
 * location-permission screens. The art drifts with the swipe (parallax) and
 * each page scrolls if large text needs more room.
 */
@Composable
fun WatchTour(onDone: () -> Unit) {
    val pages = listOf(
        TourPage("Hold for SOS", "Hold SOS until the ring fills, or shake your watch.") { HoldArt() },
        TourPage("You can cancel", "A 3-second countdown follows. Tap CANCEL if it was a mistake.") { CountdownArt() },
        TourPage("Keep it ready", "Keep your watch charged and online so your guardian gets updates.") { BatteryArt() },
        TourPage("Allow location", "Next, choose \"Allow all the time\" so it keeps working after a restart.") { PinArt() },
    )
    val pager = rememberPagerState(pageCount = { pages.size })

    // The tour hides the time so the page dots have the top edge to themselves.
    ScreenScaffold(timeText = {}) {
    Box(Modifier.fillMaxSize().background(SafeTrackColors.Background)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->
            val page = pages[index]
            val offset = pageOffset(pager, index)
            val listState = rememberScalingLazyListState(initialCenterItemIndex = 2)
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "Tour page ${index + 1} of ${pages.size}. ${page.title}. ${page.body}" },
                contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item { PageDots(count = pages.size, current = pager.currentPage) }
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .graphicsLayer {
                                // Parallax: the art moves further than the text while swiping.
                                translationX = offset * 60.dp.toPx()
                                alpha = 1f - offset.absoluteValue.coerceAtMost(1f) * 0.6f
                            },
                    ) { page.art() }
                }
                item { Box(Modifier.roundInset(0.08f)) { ScreenTitle(page.title) } }
                item { Box(Modifier.roundInset(0.10f)) { BodyText(page.body, maxLines = 4) } }
                item {
                    if (index == pages.lastIndex) {
                        PillButton(label = "CONTINUE", onClick = onDone, modifier = Modifier.padding(top = 4.dp).widthIn(max = 130.dp))
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
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                        )
                    }
                }
            }
        }

    }
    }
}

/** Page dots; the current one stretches into a pill. */
@Composable
private fun PageDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val selected = index == current
            val width by animateDpAsState(if (selected) 16.dp else 6.dp, tween(250), label = "dot-width")
            val color by animateColorAsState(if (selected) SafeTrackColors.Green else SafeTrackColors.MintDeep, tween(250), label = "dot-color")
            Box(
                Modifier
                    .height(6.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

private fun pageOffset(pager: PagerState, index: Int): Float =
    (pager.currentPage - index) + pager.currentPageOffsetFraction

@Composable
private fun loop(): Float = rememberRipple(1600)

@Composable
private fun HoldArt() {
    val progress = loop()
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 5.dp.toPx()
        val arc = Size(size.width - stroke, size.height - stroke)
        drawArc(SafeTrackColors.DangerSoft, 0f, 360f, false, Offset(stroke / 2, stroke / 2), arc, style = Stroke(stroke))
        drawArc(SafeTrackColors.Danger, -90f, 360f * progress, false, Offset(stroke / 2, stroke / 2), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        drawCircle(
            brush = Brush.verticalGradient(listOf(SafeTrackColors.DangerBright, SafeTrackColors.Danger)),
            radius = size.minDimension / 2 - 10.dp.toPx(),
        )
    }
    Text("SOS", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
}

@Composable
private fun CountdownArt() {
    val progress = loop()
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 5.dp.toPx()
        val arc = Size(size.width - stroke, size.height - stroke)
        drawCircle(SafeTrackColors.DangerSoft, radius = size.minDimension / 2 - stroke)
        drawArc(SafeTrackColors.DangerSoft, 0f, 360f, false, Offset(stroke / 2, stroke / 2), arc, style = Stroke(stroke))
        drawArc(SafeTrackColors.Danger, -90f, 360f * (1f - progress), false, Offset(stroke / 2, stroke / 2), arc, style = Stroke(stroke, cap = StrokeCap.Round))
    }
    Text(
        (3 - (progress * 3).toInt().coerceAtMost(2)).toString(),
        fontSize = 24.sp,
        fontWeight = FontWeight.ExtraBold,
        color = SafeTrackColors.Danger,
    )
}

@Composable
private fun BatteryArt() {
    val progress = loop()
    val level = 0.4f + 0.6f * progress
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 5.dp.toPx()
        val arc = Size(size.width - stroke, size.height - stroke)
        drawCircle(SafeTrackColors.Mint, radius = size.minDimension / 2 - stroke)
        drawArc(SafeTrackColors.Track, 0f, 360f, false, Offset(stroke / 2, stroke / 2), arc, style = Stroke(stroke))
        drawArc(SafeTrackColors.GreenBright, -90f, 360f * level, false, Offset(stroke / 2, stroke / 2), arc, style = Stroke(stroke, cap = StrokeCap.Round))
    }
    // A small battery in the middle of the ring.
    Canvas(Modifier.size(width = 26.dp, height = 14.dp)) {
        val stroke = 2.dp.toPx()
        drawRoundRect(
            SafeTrackColors.Green,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - 4.dp.toPx(), size.height - stroke),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(stroke),
        )
        drawRect(SafeTrackColors.Green, topLeft = Offset(size.width - 3.dp.toPx(), size.height * 0.3f), size = Size(3.dp.toPx(), size.height * 0.4f))
        drawRect(SafeTrackColors.Green, topLeft = Offset(4.dp.toPx(), 4.dp.toPx()), size = Size((size.width - 12.dp.toPx()) * level, size.height - 8.dp.toPx()))
    }
}

@Composable
private fun PinArt() {
    val progress = loop()
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(SafeTrackColors.GreenBright.copy(alpha = 0.35f * (1f - progress)), radius = size.minDimension / 2 * (0.45f + 0.55f * progress))
        drawCircle(SafeTrackColors.Green, radius = 13.dp.toPx())
        drawCircle(Color.White, radius = 5.dp.toPx())
    }
}
