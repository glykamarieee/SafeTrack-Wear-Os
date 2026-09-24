package com.safetrack.watch.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.safetrack.watch.domain.model.PendingSos
import com.safetrack.watch.domain.model.Session
import com.safetrack.watch.domain.model.WatchLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.safeTrackStore: DataStore<Preferences> by preferencesDataStore(name = "safetrack")

/** Last known sync state, restored after an app restart or reboot. */
data class SyncState(
    val lastSyncAtMillis: Long?,
    val safeZoneStatus: String?,
    val safeZoneName: String?,
    val lastLocationAtMillis: Long?,
)

/**
 * Everything the watch persists. The device token is stored encrypted with a
 * Keystore key; nothing else stored here is secret.
 */
class LocalStore(context: Context, private val cipher: TokenCipher) {

    private val store = context.applicationContext.safeTrackStore
    private val json = Json { ignoreUnknownKeys = true }
    private val sosListSerializer = ListSerializer(PendingSos.serializer())

    suspend fun watchId(): String? = store.data.first()[WATCH_ID]

    suspend fun saveWatchId(watchId: String) {
        store.edit { it[WATCH_ID] = watchId }
    }

    suspend fun loadSession(): Session? {
        val prefs = store.data.first()
        val token = prefs[TOKEN]?.let(cipher::decrypt) ?: return null
        return Session(
            watchId = prefs[WATCH_ID] ?: return null,
            deviceToken = token,
            deviceId = prefs[DEVICE_ID] ?: return null,
            childId = prefs[CHILD_ID] ?: return null,
            childName = prefs[CHILD_NAME].orEmpty(),
        )
    }

    suspend fun saveSession(session: Session) {
        val encrypted = cipher.encrypt(session.deviceToken)
        store.edit {
            it[WATCH_ID] = session.watchId
            it[TOKEN] = encrypted
            it[DEVICE_ID] = session.deviceId
            it[CHILD_ID] = session.childId
            it[CHILD_NAME] = session.childName
            it.remove(LAST_SYNC_AT)
            it.remove(SAFE_ZONE_STATUS)
            it.remove(SAFE_ZONE_NAME)
            it.remove(PENDING_LOCATION)
        }
    }

    /** Forgets the link to the child. The Watch ID and pending SOS records are kept. */
    suspend fun clearSession() {
        store.edit {
            it.remove(TOKEN)
            it.remove(DEVICE_ID)
            it.remove(CHILD_ID)
            it.remove(CHILD_NAME)
            it.remove(LAST_SYNC_AT)
            it.remove(SAFE_ZONE_STATUS)
            it.remove(SAFE_ZONE_NAME)
            it.remove(PENDING_LOCATION)
        }
    }

    suspend fun syncState(): SyncState {
        val prefs = store.data.first()
        return SyncState(prefs[LAST_SYNC_AT], prefs[SAFE_ZONE_STATUS], prefs[SAFE_ZONE_NAME], prefs[LAST_LOCATION_AT])
    }

    /** Saves a successful sync; the safe zone only when [safeZoneStatus] is known. */
    suspend fun saveSync(atMillis: Long, safeZoneStatus: String?, safeZoneName: String?) {
        store.edit {
            it[LAST_SYNC_AT] = atMillis
            if (safeZoneStatus != null) {
                it[SAFE_ZONE_STATUS] = safeZoneStatus
                if (safeZoneName != null) it[SAFE_ZONE_NAME] = safeZoneName else it.remove(SAFE_ZONE_NAME)
            }
        }
    }

    suspend fun saveLastLocationAt(atMillis: Long) {
        store.edit { it[LAST_LOCATION_AT] = atMillis }
    }

    // --- Offline location: only the newest unsent fix is kept. ---

    suspend fun pendingLocation(): WatchLocation? =
        store.data.first()[PENDING_LOCATION]?.let {
            runCatching { json.decodeFromString(WatchLocation.serializer(), it) }.getOrNull()
        }

    suspend fun setPendingLocation(location: WatchLocation?) {
        store.edit {
            if (location == null) it.remove(PENDING_LOCATION)
            else it[PENDING_LOCATION] = json.encodeToString(WatchLocation.serializer(), location)
        }
    }

    // --- Pending SOS queue ---

    val pendingSos: Flow<List<PendingSos>> = store.data.map { decodeSos(it[PENDING_SOS]) }

    suspend fun updatePendingSos(transform: (List<PendingSos>) -> List<PendingSos>) {
        store.edit {
            val updated = transform(decodeSos(it[PENDING_SOS]))
            if (updated.isEmpty()) it.remove(PENDING_SOS)
            else it[PENDING_SOS] = json.encodeToString(sosListSerializer, updated)
        }
    }

    private fun decodeSos(raw: String?): List<PendingSos> =
        raw?.let { runCatching { json.decodeFromString(sosListSerializer, it) }.getOrNull() }.orEmpty()

    private companion object {
        val WATCH_ID = stringPreferencesKey("watch_id")
        val TOKEN = stringPreferencesKey("device_token_enc")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val CHILD_ID = stringPreferencesKey("child_id")
        val CHILD_NAME = stringPreferencesKey("child_name")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val SAFE_ZONE_STATUS = stringPreferencesKey("safe_zone_status")
        val SAFE_ZONE_NAME = stringPreferencesKey("safe_zone_name")
        val LAST_LOCATION_AT = longPreferencesKey("last_location_at")
        val PENDING_LOCATION = stringPreferencesKey("pending_location")
        val PENDING_SOS = stringPreferencesKey("pending_sos")
    }
}
