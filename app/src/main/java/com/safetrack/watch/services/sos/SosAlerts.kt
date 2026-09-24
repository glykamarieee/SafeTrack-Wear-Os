package com.safetrack.watch.services.sos

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.safetrack.watch.R
import com.safetrack.watch.app.MainActivity

/**
 * Makes an SOS countdown noticeable even when the screen is off or the app is
 * in the background: vibration, plus a full-screen notification that opens the
 * countdown and has a Cancel action.
 */
class SosAlerts(context: Context) {

    private val appContext = context.applicationContext
    private val notifications = appContext.getSystemService(NotificationManager::class.java)
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Vibrator::class.java)
    }

    init {
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, appContext.getString(R.string.channel_sos), NotificationManager.IMPORTANCE_HIGH)
                .apply { enableVibration(false) },
        )
    }

    fun showCountdown() {
        // Already on screen: the countdown is drawn over whatever is showing.
        if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return

        val open = PendingIntent.getActivity(
            appContext,
            0,
            MainActivity.sosIntent(appContext),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancel = PendingIntent.getBroadcast(
            appContext,
            0,
            Intent(appContext, SosCancelReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(appContext.getString(R.string.sos_notification_title))
            .setContentText(appContext.getString(R.string.sos_notification_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .addAction(0, appContext.getString(R.string.sos_cancel), cancel)
            .build()

        try {
            notifications.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "SOS notification not allowed", e)
        }

        // Allowed only in some background states; otherwise the full-screen intent opens it.
        try {
            appContext.startActivity(MainActivity.sosIntent(appContext))
        } catch (e: Exception) {
            Log.i(TAG, "Countdown screen will open from the notification")
        }
    }

    fun dismiss() {
        notifications.cancel(NOTIFICATION_ID)
    }

    fun tick() = vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))

    fun confirmed() = vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 150, 400), -1))

    fun cancelled() = vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))

    private fun vibrate(effect: VibrationEffect) {
        try {
            vibrator?.vibrate(effect)
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }

    private companion object {
        const val TAG = "SosAlerts"
        const val CHANNEL_ID = "sos"
        const val NOTIFICATION_ID = 2
    }
}
