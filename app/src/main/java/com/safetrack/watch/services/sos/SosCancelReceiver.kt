package com.safetrack.watch.services.sos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.safetrack.watch.SafeTrackApp

/** The notification's "Cancel SOS" action. */
class SosCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as SafeTrackApp).container.sosController.cancel()
    }
}
