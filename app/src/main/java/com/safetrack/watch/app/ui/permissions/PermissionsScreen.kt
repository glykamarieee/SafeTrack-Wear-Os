package com.safetrack.watch.app.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.safetrack.watch.app.theme.SafeTrackColors
import com.safetrack.watch.app.ui.components.BodyText
import com.safetrack.watch.app.ui.components.PillButton
import com.safetrack.watch.app.ui.components.PillStyle
import com.safetrack.watch.app.ui.components.ScreenTitle
import com.safetrack.watch.app.ui.components.entrance
import com.safetrack.watch.app.ui.components.rememberRipple
import com.safetrack.watch.app.ui.components.roundInset

object Permissions {
    val foreground: Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    fun hasLocation(context: Context) = granted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
        granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasBackgroundLocation(context: Context) = granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

/**
 * Existing permission flow, redesigned for the watch display. No permission or
 * monitoring behavior is changed.
 */
@Composable
fun PermissionsScreen(needsForeground: Boolean, onChanged: () -> Unit, onSkipBackground: () -> Unit) {
    val context = LocalContext.current
    var denied by rememberSaveable { mutableStateOf(false) }
    // After repeated refusals Android stops showing the request; settings is then the only way.
    var backgroundDenied by rememberSaveable { mutableStateOf(false) }

    val foregroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        denied = !Permissions.hasLocation(context)
        onChanged()
    }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        backgroundDenied = !Permissions.hasBackgroundLocation(context)
        onChanged()
    }

    val title = if (needsForeground) "Allow location" else "Keep SafeTrack on"
    val body = if (needsForeground) {
        "SafeTrack uses your location for guardian updates, safe places and SOS."
    } else {
        "Choose \"Allow all the time\" so it keeps working when the screen is off or after a restart."
    }
    val settingsNeeded = (needsForeground && denied) || (!needsForeground && backgroundDenied)
    val ripple = rememberRipple(2200)

    // Opens centred on the icon and text; ALLOW peeks in below.
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    Box(Modifier.fillMaxSize().background(SafeTrackColors.Background)) {
        ScreenScaffold(scrollState = listState) { contentPadding ->
            ScalingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.roundInset(0.06f).entrance(0)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
                        Canvas(Modifier.fillMaxSize()) {
                            if (ripple > 0f) {
                                drawCircle(
                                    color = SafeTrackColors.GreenBright.copy(alpha = 0.4f * (1f - ripple)),
                                    radius = size.minDimension / 2 * (0.6f + 0.4f * ripple),
                                    style = Stroke(2.dp.toPx()),
                                )
                            }
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Brush.verticalGradient(listOf(SafeTrackColors.GreenBright, SafeTrackColors.Green))),
                        ) {
                            Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                        ScreenTitle(title)
                        BodyText(body, maxLines = 4)
                    }
                }
                item {
                    PillButton(
                        label = if (settingsNeeded) "OPEN SETTINGS" else "ALLOW",
                        onClick = {
                            when {
                                needsForeground && denied -> context.startActivity(appSettings(context))
                                !needsForeground && backgroundDenied -> context.startActivity(appSettings(context))
                                needsForeground -> foregroundLauncher.launch(Permissions.foreground)
                                else -> backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            }
                        },
                        modifier = Modifier.roundInset(0.06f).widthIn(max = 160.dp).entrance(2),
                    )
                }
                if (!needsForeground) {
                    item {
                        PillButton(
                            label = "LATER",
                            onClick = onSkipBackground,
                            style = PillStyle.Tonal,
                            modifier = Modifier.roundInset(0.06f).widthIn(max = 160.dp),
                        )
                    }
                }
                item {
                    Box(Modifier.roundInset(0.08f)) {
                        Text(
                            if (needsForeground) {
                                "Location works when the watch can find it and has permission."
                            } else {
                                "Later keeps the permissions you already gave."
                            },
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            color = SafeTrackColors.TextFaint,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                        )
                    }
                }
            }
        }
    }
}

private fun appSettings(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
