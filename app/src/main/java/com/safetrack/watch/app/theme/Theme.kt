package com.safetrack.watch.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography
import com.safetrack.watch.R

/**
 * SafeTrack's Wear OS palette, matching the phone app's dark theme.
 *
 * The watch is always true black (less glare, less power on OLED). Neon green
 * is for monitoring and safe states, amber for attention, coral red only for
 * SOS. Every text colour passes WCAG AA on black.
 */
object SafeTrackColors {
    val Green = Color(0xFF3DDC84)
    val GreenStrong = Color(0xFF22B35E)
    val GreenLight = Color(0xFF7CEBAE)
    val Sage = Color(0xFF9FC3AE)
    val Mint = Color(0xFFCFE6D6)

    val Background = Color(0xFF000000)
    val SurfaceDark = Color(0xFF0E1A14)
    val SurfaceRaised = Color(0xFF12201A)
    val SurfaceAccent = Color(0xFF163D2B)
    val Divider = Color(0xFF1E3329)

    val TextPrimary = Color(0xFFF2F4EF)
    val TextMuted = Color(0xFFA3ABA4)
    val TextFaint = Color(0xFF8C948F)

    val Safe = Color(0xFF3DDC84)
    val Danger = Color(0xFFFF6B5C)
    val DangerDeep = Color(0xFF3A1512)
    val Warning = Color(0xFFF2B94B)
    val Neutral = Color(0xFF97A89E)
}

/** Plus Jakarta Sans, the font used across SafeTrack (bundled in res/font). */
val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
    // Black requests use the heaviest bundled weight.
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.Black),
)

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

private val typography = Typography(defaultFontFamily = PlusJakartaSans)

@Composable
fun SafeTrackTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
