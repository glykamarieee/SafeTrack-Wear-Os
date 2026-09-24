package com.safetrack.watch

import android.app.Application
import com.safetrack.watch.services.sos.SyncWorker
import kotlinx.coroutines.flow.first

class SafeTrackApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // After an app restart, make sure nothing confirmed is left unsent.
        container.launchWhenLoaded { paired ->
            if (paired && container.sos.pending.first().isNotEmpty()) {
                SyncWorker.scheduleSosRetry(this)
            }
        }
    }
}
