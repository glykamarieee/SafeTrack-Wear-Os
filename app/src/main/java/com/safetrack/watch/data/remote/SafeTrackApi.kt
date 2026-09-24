package com.safetrack.watch.data.remote

import android.util.Log
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** PostgREST error body for a failed function call. */
@Serializable
private data class RpcErrorBody(
    val code: String? = null,
    val message: String? = null,
    val hint: String? = null,
)

sealed interface ApiResult<out T> {
    data class Success<T>(val body: T) : ApiResult<T>

    /**
     * SafeTrack answered with an error. [hint] is the machine-readable reason the
     * watch functions attach (e.g. "no_active_code", "device_unlinked").
     */
    data class HttpError(
        val status: Int,
        val code: String? = null,
        val hint: String? = null,
        val message: String? = null,
    ) : ApiResult<Nothing>

    /** SafeTrack could not be reached (no network, timeout, DNS, TLS). */
    data object Unreachable : ApiResult<Nothing>
}

/**
 * Calls the SafeTrack watch database functions through the Supabase REST API:
 *   pair_watch, record_watch_heartbeat, record_watch_location, trigger_watch_sos
 * (supabase/migrations/20260929_watch_rpc.sql).
 *
 * Only the publishable key is used, which is public by design; every call except
 * pair_watch is authenticated inside the database by Watch ID + device token.
 */
class SafeTrackApi(baseUrl: String, private val publishableKey: String) {

    private val rpcUrl = baseUrl.trimEnd('/') + "/rest/v1/rpc"
    val isConfigured: Boolean = baseUrl.startsWith("https://") && publishableKey.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun rpc(function: String, params: JsonObject): ApiResult<JsonObject> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext ApiResult.HttpError(0, message = "SafeTrack is not configured")

        val request = Request.Builder()
            .url("$rpcUrl/$function")
            .header("apikey", publishableKey)
            .header("Content-Type", "application/json")
            .post(params.toString().toRequestBody(JSON_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    ApiResult.Success(json.parseToJsonElement(text) as? JsonObject ?: JsonObject(emptyMap()))
                } else {
                    val error = runCatching { json.decodeFromString(RpcErrorBody.serializer(), text) }.getOrNull()
                    Log.w(TAG, "$function failed: HTTP ${response.code} ${error?.code} ${error?.hint} ${error?.message}")
                    ApiResult.HttpError(response.code, error?.code, error?.hint, error?.message)
                }
            }
        } catch (e: IOException) {
            Log.i(TAG, "$function unreachable: ${e.javaClass.simpleName}")
            ApiResult.Unreachable
        } catch (e: Exception) {
            // Malformed response body.
            Log.w(TAG, "$function returned an unexpected response", e)
            ApiResult.HttpError(-1)
        }
    }

    private companion object {
        const val TAG = "SafeTrackApi"
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
