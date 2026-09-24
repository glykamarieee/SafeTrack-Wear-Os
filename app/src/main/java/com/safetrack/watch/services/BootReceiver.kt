package com.safetrack.watch.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.safetrack.watch.SafeTrackApp
import com.safetrack.watch.services.sos.SyncWorker
import kotlinx.coroutines.flow.first

/** Resumes monitoring after a reboot or app update, without re-pairing. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val app = context.applicationContext as SafeTrackApp
        val pending = goAsync()
        app.container.launchWhenLoaded { paired ->
            try {
                if (paired) {
                    SyncWorker.schedulePeriodic(app)
                    if (app.container.sos.pending.first().isNotEmpty()) SyncWorker.scheduleSosRetry(app)
                    if (app.container.location.hasPermission()) MonitoringService.start(app)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
