package com.safetrack.watch.data.local

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Provides the watch's SafeTrack identifier, ST-WATCH-XXXXXXXX.
 *
 * It is derived once from ANDROID_ID and then persisted, so it stays the same
 * across app restarts, reboots and app updates. Uninstalling the app can reset
 * ANDROID_ID (observed on the Wear OS API 30 emulator), giving a new Watch ID
 * that the guardian must link again.
 * The guardian enters this ID in the Guardian app to link the watch.
 */
class WatchIdProvider(private val context: Context, private val store: LocalStore) {

    private val mutex = Mutex()

    suspend fun get(): String = mutex.withLock {
        store.watchId()?.let { return it }
        val id = PREFIX + suffixFrom(seed())
        store.saveWatchId(id)
        id
    }

    @SuppressLint("HardwareIds")
    private fun seed(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return if (androidId.isNullOrBlank() || androidId == BROKEN_ANDROID_ID) {
            UUID.randomUUID().toString()
        } else {
            "safetrack-watch:$androidId"
        }
    }

    private fun suffixFrom(seed: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
        return (0 until SUFFIX_LENGTH)
            .map { ALPHABET[(digest[it].toInt() and 0xFF) % ALPHABET.length] }
            .joinToString("")
    }

    private companion object {
        const val PREFIX = "ST-WATCH-"
        const val SUFFIX_LENGTH = 8
        // No 0/O, 1/I/L: easy for a guardian to read off the watch.
        const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
        const val BROKEN_ANDROID_ID = "9774d56d682e549c"
    }
}
