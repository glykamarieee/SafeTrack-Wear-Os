package com.safetrack.watch.data.repository

import com.safetrack.watch.data.local.DeviceStatusReader
import com.safetrack.watch.data.local.LocalStore
import com.safetrack.watch.data.remote.ApiResult
import com.safetrack.watch.data.remote.SafeTrackApi
import com.safetrack.watch.data.remote.obj
import com.safetrack.watch.data.remote.string
import com.safetrack.watch.domain.model.SafeZoneStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The single path to the authenticated watch functions (record_watch_heartbeat,
 * record_watch_location, trigger_watch_sos). Adds the Watch ID, device token and
 * battery/network/model to every call, tracks whether SafeTrack is reachable,
 * and ends the session when SafeTrack says the watch is no longer linked.
 */
class WatchLink(
    private val api: SafeTrackApi,
    private val store: LocalStore,
    private val devices: DeviceRepositoryImpl,
    private val status: DeviceStatusReader,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class State(
        val lastSuccessAtMillis: Long? = null,
        val inFlight: Int = 0,
        val lastAttemptFailed: Boolean = false,
        val safeZone: SafeZoneStatus = SafeZoneStatus.Unavailable(SafeZoneStatus.Reason.NOT_CHECKED),
    )

    sealed interface Result {
        data class Delivered(val response: JsonObject) : Result

        /** Not delivered. [retryable] is false when resending the same call cannot succeed. */
        data class Failed(val retryable: Boolean) : Result
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    suspend fun restore() {
        val saved = store.syncState()
        _state.update {
            it.copy(
                lastSuccessAtMillis = saved.lastSyncAtMillis,
                safeZone = SafeZoneStatus.fromServer(saved.safeZoneStatus, saved.safeZoneName) ?: it.safeZone,
            )
        }
    }

    fun reset() {
        _state.value = State()
    }

    suspend fun send(function: String, params: JsonObjectBuilder.() -> Unit = {}): Result {
        val session = devices.session.value ?: return Result.Failed(retryable = true)
        val device = status.read()

        val body = buildJsonObject {
            put("p_watch_id", session.watchId)
            put("p_device_token", session.deviceToken)
            params()
            device.batteryPercent?.let { put("p_battery_percent", it) }
            put("p_network_type", device.network.wireValue)
            put("p_device_model", status.deviceModel)
        }

        _state.update { it.copy(inFlight = it.inFlight + 1) }
        val result = try {
            api.rpc(function, body)
        } finally {
            _state.update { it.copy(inFlight = it.inFlight - 1) }
        }

        return when (result) {
            is ApiResult.Success -> {
                val now = clock()
                val zoneJson = result.body.obj("safeZone")
                val zoneStatus = zoneJson?.string("status")
                val zoneName = zoneJson?.string("zoneName")
                val zone = SafeZoneStatus.fromServer(zoneStatus, zoneName)
                store.saveSync(now, if (zone != null) zoneStatus else null, zoneName)
                _state.update {
                    it.copy(lastSuccessAtMillis = now, lastAttemptFailed = false, safeZone = zone ?: it.safeZone)
                }
                Result.Delivered(result.body)
            }
            is ApiResult.HttpError -> {
                _state.update { it.copy(lastAttemptFailed = true) }
                // Only watch_device()'s own answer ends the session, never a gateway or permission error.
                if (result.code == UNLINKED_SQLSTATE || result.hint == "device_unlinked") {
                    devices.invalidate()
                    reset()
                }
                // 400 means SafeTrack refused this call's content; resending it will not help.
                Result.Failed(retryable = result.status != 400)
            }
            ApiResult.Unreachable -> {
                _state.update { it.copy(lastAttemptFailed = true) }
                Result.Failed(retryable = true)
            }
        }
    }

    private companion object {
        const val UNLINKED_SQLSTATE = "28000"
    }
}
