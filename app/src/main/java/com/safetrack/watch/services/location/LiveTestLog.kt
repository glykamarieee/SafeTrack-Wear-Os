package com.safetrack.watch.services.location

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * Field-test log (2026-11-08): one CSV line per location event, so an outdoor
 * test without a laptop can be checked afterwards:
 *
 *   adb pull /sdcard/Android/data/com.safetrack.watch/files/safetrack-test.csv
 *
 * Kept small: at most [MAX_BYTES] (the older half moves to a .1 file). Stays
 * on the watch (never sent anywhere). Turn it off for the defense with
 *
 *   adb shell touch /sdcard/Android/data/com.safetrack.watch/files/testlog.off
 */
class LiveTestLog(context: Context) {
    private val dir: File? = context.getExternalFilesDir(null)
    private val file = dir?.let { File(it, "safetrack-test.csv") }
    private val writer = Executors.newSingleThreadExecutor()
    private val time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private val enabled: Boolean get() = dir != null && !File(dir, "testlog.off").exists()

    /** event, then name=value pairs; nulls are left empty. */
    fun write(event: String, vararg fields: Pair<String, Any?>) {
        val target = file ?: return
        val line = buildString {
            append(time.format(Date())).append(',').append(event)
            fields.forEach { (key, value) ->
                append(',').append(key).append('=')
                when (value) {
                    null -> Unit
                    is Float -> append(String.format(Locale.US, "%.2f", value))
                    is Double -> append(String.format(Locale.US, "%.6f", value))
                    else -> append(value)
                }
            }
            append('\n')
        }
        writer.execute {
            try {
                if (!enabled) return@execute
                if (target.length() > MAX_BYTES) target.renameTo(File(target.parentFile, target.name + ".1"))
                target.appendText(line)
            } catch (e: Exception) {
                Log.d(TAG, "Test log write failed: ${e.message}")
            }
        }
    }

    private companion object {
        const val TAG = "LiveTestLog"
        const val MAX_BYTES = 1_500_000L
    }
}
