package com.safetrack.watch.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/**
 * SafeTrack's Wear OS palette.
 *
 * The watch stays intentionally dark to reduce glare and power use. Green is
 * reserved for monitoring/safe states, amber for attention, and red for SOS.
 */
object SafeTrackColors {
    val Green = Color(0xFF20B97A)
    val GreenStrong = Color(0xFF0E8B5B)
    val GreenLight = Color(0xFF72D9AA)
    val Sage = Color(0xFF8CB8A3)
    val Mint = Color(0xFFC9F2DE)

    val Background = Color(0xFF050907)
    val SurfaceDark = Color(0xFF0D1511)
    val SurfaceRaised = Color(0xFF14211A)
    val SurfaceAccent = Color(0xFF173426)
    val Divider = Color(0xFF26352E)

    val TextPrimary = Color(0xFFF4F8F5)
    val TextMuted = Color(0xFFAAB8B0)
    val TextFaint = Color(0xFF718078)

    val Safe = Color(0xFF57D997)
    val Danger = Color(0xFFFF6468)
    val DangerDeep = Color(0xFF8D2029)
    val Warning = Color(0xFFFFC857)
    val Neutral = Color(0xFF9EACA5)
}

private val scheme = ColorScheme(
    primary = SafeTrackColors.Green,
    primaryDim = SafeTrackColors.GreenStrong,
    primaryContainer = SafeTrackColors.SurfaceAccent,
    onPrimary = Color.Black,
    onPrimaryContainer = SafeTrackColors.Mint,
    secondary = SafeTrackColors.Sage,
    onSecondary = Color.Black,
    secondaryContainer = SafeTrackColors.SurfaceRaised,
    onSecondaryContainer = SafeTrackColors.Mint,
    surfaceContainerLow = SafeTrackColors.SurfaceDark,
    surfaceContainer = SafeTrackColors.SurfaceDark,
    surfaceContainerHigh = SafeTrackColors.SurfaceRaised,
    onSurface = SafeTrackColors.TextPrimary,
    onSurfaceVariant = SafeTrackColors.TextMuted,
    background = SafeTrackColors.Background,
    onBackground = SafeTrackColors.TextPrimary,
    error = SafeTrackColors.Danger,
    onError = Color.Black,
)

@Composable
fun SafeTrackTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
