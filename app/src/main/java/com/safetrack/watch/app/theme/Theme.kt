package com.safetrack.watch.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/** SafeTrack's green/sage identity on a true-black watch background. */
object SafeTrackColors {
    val Green = Color(0xFF1EA96B)
    val GreenLight = Color(0xFF5BCB91)
    val Sage = Color(0xFF70A982)
    val Mint = Color(0xFFA7DDBB)
    val SurfaceDark = Color(0xFF12241C)
    val SurfaceRaised = Color(0xFF1B3328)

    // Status colours, chosen for contrast on black.
    val Safe = Color(0xFF4ADE80)
    val Danger = Color(0xFFFF5A5A)
    val DangerDeep = Color(0xFFB3261E)
    val Warning = Color(0xFFFFC53D)
    val Neutral = Color(0xFFB8C4BE)
    val TextMuted = Color(0xFFA9B8B0)
}

private val scheme = ColorScheme(
    primary = SafeTrackColors.Green,
    primaryDim = SafeTrackColors.Sage,
    primaryContainer = SafeTrackColors.SurfaceRaised,
    onPrimary = Color.White,
    onPrimaryContainer = SafeTrackColors.Mint,
    secondary = SafeTrackColors.Sage,
    onSecondary = Color.Black,
    secondaryContainer = SafeTrackColors.SurfaceRaised,
    onSecondaryContainer = SafeTrackColors.Mint,
    surfaceContainerLow = SafeTrackColors.SurfaceDark,
    surfaceContainer = SafeTrackColors.SurfaceDark,
    surfaceContainerHigh = SafeTrackColors.SurfaceRaised,
    onSurface = Color.White,
    onSurfaceVariant = SafeTrackColors.TextMuted,
    background = Color.Black,
    onBackground = Color.White,
    error = SafeTrackColors.Danger,
    onError = Color.Black,
)

@Composable
fun SafeTrackTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
