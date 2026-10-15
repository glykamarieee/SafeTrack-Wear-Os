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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.safetrack.watch.app.theme.SafeTrackColors
import com.safetrack.watch.app.ui.components.Eyebrow
import com.safetrack.watch.app.ui.components.SafeTrackBrand

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

    val title = if (needsForeground) "Allow location" else "Keep monitoring active"
    val body = if (needsForeground) {
        "SafeTrack uses the watch location for guardian updates, safe-zone status, and confirmed SOS alerts."
    } else {
        "Allow location all the time so monitoring can continue when the display is off or after the watch restarts."
    }

    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { SafeTrackBrand(compact = true) }
            item {
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .clip(CircleShape)
                        .background(SafeTrackColors.Green.copy(alpha = 0.12f))
                        .border(1.dp, SafeTrackColors.Green.copy(alpha = 0.32f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⌖", fontSize = 34.sp, color = SafeTrackColors.GreenLight, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Eyebrow(if (needsForeground) "Location permission" else "Background monitoring")
                    Text(
                        title,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SafeTrackColors.TextPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        body,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = SafeTrackColors.TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        when {
                            needsForeground && denied -> context.startActivity(appSettings(context))
                            !needsForeground && backgroundDenied -> context.startActivity(appSettings(context))
                            needsForeground -> foregroundLauncher.launch(Permissions.foreground)
                            else -> backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SafeTrackColors.Green,
                        contentColor = Color.Black,
                    ),
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            if ((needsForeground && denied) || (!needsForeground && backgroundDenied)) "OPEN SETTINGS" else "ALLOW",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
            if (!needsForeground) {
                item {
                    Button(
                        onClick = onSkipBackground,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                    ) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("LATER", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item {
                Text(
                    if (needsForeground) {
                        "SafeTrack shows available safety information only; location availability depends on the watch and its permissions."
                    } else {
                        "Choosing Later does not add new behavior; it only continues with the permissions currently granted."
                    },
                    fontSize = 8.sp,
                    lineHeight = 11.sp,
                    color = SafeTrackColors.TextFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }
        }
    }
}

private fun appSettings(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
