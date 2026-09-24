package com.safetrack.watch

import android.content.Context
import com.safetrack.watch.data.local.DeviceStatusReader
import com.safetrack.watch.data.local.LocalStore
import com.safetrack.watch.data.local.TokenCipher
import com.safetrack.watch.data.local.WatchIdProvider
import com.safetrack.watch.data.remote.SafeTrackApi
import com.safetrack.watch.data.repository.DeviceRepositoryImpl
import com.safetrack.watch.data.repository.MonitoringRepositoryImpl
import com.safetrack.watch.data.repository.SosRepositoryImpl
import com.safetrack.watch.data.repository.WatchLink
import com.safetrack.watch.services.location.LocationTracker
import com.safetrack.watch.services.sos.SosAlerts
import com.safetrack.watch.services.sos.SosController
import com.safetrack.watch.services.sos.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Manual dependency wiring; one instance per process. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Main-thread scope that outlives screens: SOS countdowns and uploads run here. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val store = LocalStore(appContext, TokenCipher())
    private val api = SafeTrackApi(BuildConfig.SAFETRACK_URL, BuildConfig.SAFETRACK_PUBLISHABLE_KEY)
    private val status = DeviceStatusReader(appContext)

    val devices = DeviceRepositoryImpl(api, store, WatchIdProvider(appContext, store), status, appScope)
    private val link = WatchLink(api, store, devices, status)
    val monitoring = MonitoringRepositoryImpl(link, store, status, appScope)
    val sos = SosRepositoryImpl(link, store, devices)
    val location = LocationTracker(appContext, monitoring, appScope)

    val sosController = SosController(
        scope = appScope,
        devices = devices,
        sos = sos,
        location = location,
        alerts = SosAlerts(appContext),
        scheduleRetry = { SyncWorker.scheduleSosRetry(appContext) },
    )

    /** Runs [block] once the stored session has been read, with whether the watch is paired. */
    fun launchWhenLoaded(block: suspend (paired: Boolean) -> Unit) {
        appScope.launch {
            devices.loaded.first { it }
            block(devices.session.value != null)
        }
    }
}
