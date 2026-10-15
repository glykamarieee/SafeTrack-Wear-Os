package com.safetrack.watch.app.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography
import com.safetrack.watch.R

/**
 * SafeTrack's light Wear OS palette: white and soft mint surfaces, forest-green
 * text (never plain black), SafeTrack green for safe states, dark amber for
 * attention, red only for SOS. Every text colour passes WCAG AA (4.5:1) on
 * [Background] and [Surface].
 */
object SafeTrackColors {
    // Surfaces
    val Background = Color(0xFFF6FAF7)
    val Surface = Color(0xFFFFFFFF)
    val Mint = Color(0xFFE6F4EA)
    val MintDeep = Color(0xFFCDEBD7)
    val Divider = Color(0xFFDCE8E0)
    val Track = Color(0xFFE3ECE6)

    // Text
    val Forest = Color(0xFF0D2A1E)       // ~14:1 on Background
    val TextMuted = Color(0xFF3F5247)    // ~8:1
    val TextFaint = Color(0xFF5A6B61)    // ~5.4:1

    // Brand and states
    val Green = Color(0xFF15803D)        // 5.0:1 on white; white on it 5.0:1
    val GreenBright = Color(0xFF22B35E)  // decorative only (rings, glows)
    val Safe = Green
    val Warning = Color(0xFF8F5300)      // dark amber text, ~5.6:1
    val WarningFill = Color(0xFFF2B94B)
    val WarningSoft = Color(0xFFFFF3D6)
    val Neutral = Color(0xFF5A6B61)
    val NeutralSoft = Color(0xFFE9EEEB)

    // SOS only
    val Danger = Color(0xFFB42318)       // ~6.3:1; white on it ~6.5:1
    val DangerBright = Color(0xFFE5483A)
    val DangerSoft = Color(0xFFFDE8E6)
    val DangerDeep = Color(0xFF5C120B)   // countdown background
    val OnDanger = Color(0xFFFFFFFF)
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
    primaryDim = SafeTrackColors.Green,
    primaryContainer = SafeTrackColors.Mint,
    onPrimary = Color.White,
    onPrimaryContainer = SafeTrackColors.Forest,
    secondary = SafeTrackColors.TextMuted,
    onSecondary = Color.White,
    secondaryContainer = SafeTrackColors.Mint,
    onSecondaryContainer = SafeTrackColors.Forest,
    surfaceContainerLow = SafeTrackColors.Surface,
    surfaceContainer = SafeTrackColors.Surface,
    surfaceContainerHigh = SafeTrackColors.Mint,
    onSurface = SafeTrackColors.Forest,
    onSurfaceVariant = SafeTrackColors.TextMuted,
    outline = SafeTrackColors.Divider,
    outlineVariant = SafeTrackColors.Divider,
    background = SafeTrackColors.Background,
    onBackground = SafeTrackColors.Forest,
    error = SafeTrackColors.Danger,
    onError = Color.White,
)

private val typography = Typography(defaultFontFamily = PlusJakartaSans)

/**
 * True when the user turned animations off (Settings > Accessibility > Remove
 * animations sets the animator duration scale to 0). Screens then show
 * everything instantly or with a plain fade.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun SafeTrackTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduceMotion = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
