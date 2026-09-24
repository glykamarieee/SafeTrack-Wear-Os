package com.safetrack.watch.data.local

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import com.safetrack.watch.domain.model.DeviceStatus
import com.safetrack.watch.domain.model.NetworkType

/** Reads battery and network state on demand; no listeners are kept running. */
class DeviceStatusReader(context: Context) {

    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)

    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    fun read(): DeviceStatus {
        val battery = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null
        val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

        return DeviceStatus(percent, plugged, network())
    }

    fun isOnline(): Boolean = network() != NetworkType.NONE

    private fun network(): NetworkType {
        val caps = connectivity?.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
            ?: return NetworkType.NONE
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return NetworkType.NONE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            // e.g. Bluetooth through a paired phone.
            else -> NetworkType.UNKNOWN
        }
    }
}
