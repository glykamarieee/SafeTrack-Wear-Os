package com.safetrack.watch.data.repository

import android.util.Log
import com.safetrack.watch.data.local.DeviceStatusReader
import com.safetrack.watch.data.local.LocalStore
import com.safetrack.watch.data.local.WatchIdProvider
import com.safetrack.watch.data.remote.ApiResult
import com.safetrack.watch.data.remote.SafeTrackApi
import com.safetrack.watch.data.remote.string
import com.safetrack.watch.domain.model.Outcome
import com.safetrack.watch.domain.model.SafeTrackError
import com.safetrack.watch.domain.model.Session
import com.safetrack.watch.domain.repository.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DeviceRepositoryImpl(
    private val api: SafeTrackApi,
    private val store: LocalStore,
    private val watchIds: WatchIdProvider,
    private val status: DeviceStatusReader,
    scope: CoroutineScope,
) : DeviceRepository {

    private val _session = MutableStateFlow<Session?>(null)
    override val session: StateFlow<Session?> = _session.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    /** False until the stored session has been read at startup. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _unlinkNotice = MutableStateFlow<String?>(null)
    override val unlinkNotice: StateFlow<String?> = _unlinkNotice.asStateFlow()

    private val _justConnected = MutableStateFlow<String?>(null)
    override val justConnected: StateFlow<String?> = _justConnected.asStateFlow()

    init {
        scope.launch {
            watchIds.get()
            _session.value = store.loadSession()
            _loaded.value = true
        }
    }

    override suspend fun watchId(): String = watchIds.get()

    override suspend fun pair(connectionCode: String): Outcome<Session> {
        val code = connectionCode.filter(Char::isDigit)
        if (code.length != CODE_LENGTH) return Outcome.Failed(SafeTrackError.InvalidCode)
        if (!api.isConfigured) return Outcome.Failed(SafeTrackError.NotConfigured)
        if (!status.isOnline()) return Outcome.Failed(SafeTrackError.NoInternet)

        val watchId = watchIds.get()
        val params = buildJsonObject {
            put("p_watch_id", watchId)
            put("p_connection_code", code)
            put("p_device_model", status.deviceModel)
        }
        return when (val result = api.rpc("pair_watch", params)) {
            is ApiResult.Success -> {
                val body = result.body
                val token = body.string("deviceToken")
                val childId = body.string("childId")
                val deviceId = body.string("deviceId")
                if (token.isNullOrBlank() || childId.isNullOrBlank() || deviceId.isNullOrBlank()) {
                    Outcome.Failed(SafeTrackError.BackendUnavailable)
                } else {
                    val session = Session(
                        watchId = body.string("watchId") ?: watchId,
                        deviceToken = token,
                        deviceId = deviceId,
                        childId = childId,
                        childName = body.string("childName").orEmpty(),
                    )
                    try {
                        store.saveSession(session)
                    } catch (e: Exception) {
                        // SafeTrack already used the code, so the guardian must make a new one.
                        Log.e(TAG, "Paired, but the device token could not be stored", e)
                        return Outcome.Failed(SafeTrackError.StorageFailed)
                    }
                    _unlinkNotice.value = null
                    // Set before the session so the UI shows "Connected" first.
                    _justConnected.value = session.childName
                    _session.value = session
                    Outcome.Ok(session)
                }
            }
            is ApiResult.HttpError -> Outcome.Failed(pairError(result, watchId))
            ApiResult.Unreachable -> Outcome.Failed(SafeTrackError.NoInternet)
        }
    }

    /** pair_watch reports why pairing failed in the error HINT. */
    private fun pairError(error: ApiResult.HttpError, watchId: String): SafeTrackError = when (error.hint) {
        "watch_not_registered" -> SafeTrackError.WatchNotRegistered(watchId)
        "watch_not_linked" -> SafeTrackError.WatchNotLinked
        "no_active_code" -> SafeTrackError.NoActiveCode
        "code_expired", "code_invalid", "invalid_code_format" -> SafeTrackError.InvalidCode
        else -> SafeTrackError.BackendUnavailable
    }

    /** Called when SafeTrack rejects the device token (watch unlinked, disabled or re-paired elsewhere). */
    suspend fun invalidate() {
        if (_session.value == null) return
        Log.w(TAG, "SafeTrack rejected this watch's device token; returning to device connection")
        store.clearSession()
        _session.value = null
        _unlinkNotice.value = SafeTrackError.Unlinked.message
    }

    override fun acknowledgeConnected() {
        _justConnected.value = null
    }

    override fun clearUnlinkNotice() {
        _unlinkNotice.value = null
    }

    private companion object {
        const val TAG = "DeviceRepository"
        const val CODE_LENGTH = 6
    }
}
