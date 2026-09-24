package com.safetrack.watch.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.safetrack.watch.R
import com.safetrack.watch.SafeTrackApp
import com.safetrack.watch.app.MainActivity
import com.safetrack.watch.domain.model.SosActivation
import com.safetrack.watch.services.heartbeat.HeartbeatLoop
import com.safetrack.watch.services.sensors.ShakeDetector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps essential monitoring running with the screen
 * off: periodic location, heartbeat/device status, shake SOS detection and
 * pending-SOS retry when the network returns. Runs only while the watch is paired.
 */
class MonitoringService : LifecycleService() {

    private val container by lazy { (application as SafeTrackApp).container }
    private lateinit var heartbeat: HeartbeatLoop
    private lateinit var shake: ShakeDetector
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        if (!enterForeground()) {
            stopSelf()
            return
        }

        heartbeat = HeartbeatLoop(this, container.monitoring, container.sos)
        shake = ShakeDetector(this) { container.sosController.trigger(SosActivation.SHAKE) }

        container.location.start()
        shake.start()
        heartbeat.start(lifecycleScope)
        watchNetwork()

        // Stop monitoring if SafeTrack unlinks the watch.
        lifecycleScope.launch {
            container.devices.session.first { it == null }
            Log.i(TAG, "Watch no longer paired; stopping monitoring")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        if (::heartbeat.isInitialized) heartbeat.stop()
        if (::shake.isInitialized) shake.stop()
        container.location.stop()
        networkCallback?.let { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(it) }
        networkCallback = null
        super.onDestroy()
    }

    private fun enterForeground(): Boolean {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_monitoring), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.monitoring_notification_title))
            .setContentText(getString(R.string.monitoring_notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        return try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
            true
        } catch (e: Exception) {
            // Missing location permission, or started from the background where not allowed.
            Log.w(TAG, "Could not start monitoring in the foreground", e)
            false
        }
    }

    private fun watchNetwork() {
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Communication is back: pending SOS goes first.
                lifecycleScope.launch {
                    container.monitoring.refreshDeviceStatus()
                    heartbeat.syncNow(force = true)
                }
            }

            override fun onLost(network: Network) {
                lifecycleScope.launch { container.monitoring.refreshDeviceStatus() }
            }
        }
        connectivity.registerDefaultNetworkCallback(callback)
        networkCallback = callback
    }

    companion object {
        private const val TAG = "MonitoringService"
        private const val CHANNEL_ID = "monitoring"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, MonitoringService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Monitoring service could not be started now", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitoringService::class.java))
        }
    }
}
