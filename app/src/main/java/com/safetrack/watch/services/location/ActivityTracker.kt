package com.safetrack.watch.services.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult

/**
 * Android Activity Recognition (walking, running, on a bicycle, in a vehicle,
 * still), from the Play Services location library the watch already uses.
 * Optional: without the "Physical activity" permission, or on a watch without
 * the service, travel mode comes from GPS speed alone.
 */
class ActivityTracker(context: Context) {
    data class Detected(val type: Int, val confidence: Int, val atMillis: Long)

    private val appContext = context.applicationContext
    private var running = false

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (running || !hasPermission()) return
        running = true
        try {
            ActivityRecognition.getClient(appContext)
                .requestActivityUpdates(DETECTION_INTERVAL_MS, pendingIntent(appContext))
                .addOnFailureListener { e ->
                    Log.w(TAG, "Activity recognition unavailable; using GPS speed only", e)
                    running = false
                }
        } catch (e: SecurityException) {
            Log.w(TAG, "Activity recognition permission missing", e)
            running = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!running) return
        running = false
        try {
            ActivityRecognition.getClient(appContext).removeActivityUpdates(pendingIntent(appContext))
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not stop activity recognition", e)
        }
    }

    companion object {
        private const val TAG = "ActivityTracker"
        const val DETECTION_INTERVAL_MS = 15_000L

        @Volatile
        var latest: Detected? = null
            internal set

        // Mutable: Play Services fills in the result extras.
        private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            7,
            Intent(context, ActivityReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0),
        )
    }
}

class ActivityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityRecognitionResult.hasResult(intent)) return
        val activity = ActivityRecognitionResult.extractResult(intent)?.mostProbableActivity ?: return
        ActivityTracker.latest = ActivityTracker.Detected(activity.type, activity.confidence, System.currentTimeMillis())
    }
}
