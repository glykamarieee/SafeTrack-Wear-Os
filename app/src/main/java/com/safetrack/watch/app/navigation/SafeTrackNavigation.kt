package com.safetrack.watch.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.safetrack.watch.AppContainer
import com.safetrack.watch.app.ui.connect.ConnectScreen
import com.safetrack.watch.app.ui.connect.ConnectViewModel
import com.safetrack.watch.app.ui.connect.ConnectedScreen
import com.safetrack.watch.app.ui.dashboard.DashboardScreen
import com.safetrack.watch.app.ui.dashboard.DashboardViewModel
import com.safetrack.watch.app.ui.dashboard.SafeZoneScreen
import com.safetrack.watch.app.ui.dashboard.StatusScreen
import com.safetrack.watch.app.ui.permissions.Permissions
import com.safetrack.watch.app.ui.permissions.PermissionsScreen
import com.safetrack.watch.app.ui.sos.SosScreen
import com.safetrack.watch.services.MonitoringService
import com.safetrack.watch.services.sos.SyncWorker

private object Routes {
    const val DASHBOARD = "dashboard"
    const val SAFE_ZONE = "safe-zone"
    const val STATUS = "status"
}

/**
 * FIRST LAUNCH -> Device Connection -> Connected -> (permissions) -> Child Dashboard
 * The SOS screens are drawn on top whenever an SOS is active, from any screen.
 */
@Composable
fun SafeTrackRoot(container: AppContainer) {
    val context = LocalContext.current
    val session by container.devices.session.collectAsStateWithLifecycle()
    val justConnected by container.devices.justConnected.collectAsStateWithLifecycle()
    val sosState by container.sosController.state.collectAsStateWithLifecycle()

    // Re-check permissions whenever the app returns to the foreground.
    var permissionCheck by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionCheck++ }
    val hasLocation = remember(permissionCheck) { Permissions.hasLocation(context) }
    val hasBackground = remember(permissionCheck) { Permissions.hasBackgroundLocation(context) }
    var backgroundSkipped by rememberSaveable { mutableStateOf(false) }

    val paired = session != null
    LaunchedEffect(paired, hasLocation) {
        if (paired) {
            SyncWorker.schedulePeriodic(context)
            if (hasLocation) MonitoringService.start(context)
        } else {
            SyncWorker.cancelAll(context)
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            justConnected != null -> ConnectedScreen(justConnected.orEmpty()) {
                container.devices.acknowledgeConnected()
            }

            !paired -> {
                val vm: ConnectViewModel = viewModel(
                    factory = viewModelFactory { initializer { ConnectViewModel(container.devices) } },
                )
                ConnectScreen(vm)
            }

            !hasLocation -> PermissionsScreen(
                needsForeground = true,
                onChanged = { permissionCheck++ },
                onSkipBackground = {},
            )

            !hasBackground && !backgroundSkipped -> PermissionsScreen(
                needsForeground = false,
                onChanged = { permissionCheck++ },
                onSkipBackground = { backgroundSkipped = true },
            )

            else -> MainNavigation(container)
        }

        if (paired) {
            SosScreen(
                state = sosState,
                onCancel = container.sosController::cancel,
                onDismiss = container.sosController::dismiss,
            )
        }
    }
}

@Composable
private fun MainNavigation(container: AppContainer) {
    val navController = rememberSwipeDismissableNavController()
    val dashboard: DashboardViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DashboardViewModel(container.devices, container.monitoring, container.sosController) }
        },
    )

    SwipeDismissableNavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                viewModel = dashboard,
                onOpenSafeZone = { navController.navigate(Routes.SAFE_ZONE) },
                onOpenStatus = { navController.navigate(Routes.STATUS) },
            )
        }
        composable(Routes.SAFE_ZONE) { SafeZoneScreen(dashboard) }
        composable(Routes.STATUS) { StatusScreen(dashboard) }
    }
}
