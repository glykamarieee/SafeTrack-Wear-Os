package com.safetrack.watch.services

import android.content.Context
import android.os.PowerManager

/** Keeps the CPU awake for one short network exchange while the screen is off. */
suspend fun <T> withWakeLock(context: Context, tag: String, block: suspend () -> T): T {
    val power = context.getSystemService(PowerManager::class.java)
    val lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SafeTrack:$tag").apply {
        setReferenceCounted(false)
        acquire(60_000L)
    }
    return try {
        block()
    } finally {
        if (lock.isHeld) lock.release()
    }
}
