package com.loaloaloa.relay

import com.loaloaloa.data.model.RelayRoom
import com.loaloaloa.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

/**
 * Hub-only directory operations against the room's Sender: list the paired spoke devices and revoke
 * one. Unlike [RelaySender] / [RelayRegistrar] — fire-and-forget pushes handed to WorkManager — these
 * are request/response calls the settings UI awaits, so they run as direct (signed) HTTP on the IO
 * dispatcher and surface a [Result] the ViewModel can render (list / empty / error).
 *
 * Every request is HMAC-signed with the room's MAC subkey (see [RelayCrypto.signRequest]), exactly
 * like `/register` and `/send`, so only a holder of the room secret can enumerate or revoke devices.
 */
interface RelayDirectory {
    /** List the room's paired spoke devices + the pair/unpair audit log (most-recent first). */
    suspend fun listDevices(room: RelayRoom): Result<RelayDevicesResponse>

    /** Drop one spoke device (by its FCM [token]) from the room so it stops receiving relays. */
    suspend fun revoke(room: RelayRoom, token: String): Result<Unit>

    /** Destroy the whole room on the Sender (hub "Huỷ phòng") so every spoke is kicked on next poll. */
    suspend fun closeRoom(room: RelayRoom): Result<Unit>
}

/**
 * Production [RelayDirectory] over the shared [OkHttpClient]. Each call signs the JSON body with the
 * room's MAC subkey and POSTs it to the matching Sender endpoint; non-2xx or any IO error becomes a
 * [Result.failure] so the UI can show a retry rather than crash.
 */
@Singleton
class OkHttpRelayDirectory @Inject constructor(
    private val client: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RelayDirectory {

    override suspend fun listDevices(room: RelayRoom): Result<RelayDevicesResponse> =
        withContext(ioDispatcher) {
            val secret = decodeSecret(room) ?: return@withContext Result.failure(
                IllegalStateException("room not paired / bad secret"),
            )
            val body = RelayJson.encodeDevicesRequest(RelayDevicesRequest(roomId = room.roomId))
            post(room, "/devices", body, secret).mapCatching { responseText ->
                RelayJson.decodeDevicesResponse(responseText)
                    ?: throw IllegalStateException("malformed /devices response")
            }
        }

    override suspend fun revoke(room: RelayRoom, token: String): Result<Unit> =
        withContext(ioDispatcher) {
            val secret = decodeSecret(room) ?: return@withContext Result.failure(
                IllegalStateException("room not paired / bad secret"),
            )
            val body = RelayJson.encodeRevoke(RelayRevokeRequest(roomId = room.roomId, token = token))
            post(room, "/revoke", body, secret).map { }
        }

    override suspend fun closeRoom(room: RelayRoom): Result<Unit> =
        withContext(ioDispatcher) {
            val secret = decodeSecret(room) ?: return@withContext Result.failure(
                IllegalStateException("room not paired / bad secret"),
            )
            val body = RelayJson.encodeDevicesRequest(RelayDevicesRequest(roomId = room.roomId))
            post(room, "/close", body, secret).map { }
        }

    /** Decode the room's base64url secret, or null when the room is unpaired / malformed. */
    private fun decodeSecret(room: RelayRoom): ByteArray? {
        if (room.roomId.isBlank() || room.roomSecret.isBlank() || room.senderUrl.isBlank()) return null
        return runCatching { RelayCrypto.decodeSecret(room.roomSecret) }.getOrNull()
    }

    /** Sign [body] and POST it to `senderUrl + path`; the raw response text on 2xx, failure otherwise. */
    private fun post(room: RelayRoom, path: String, body: String, secret: ByteArray): Result<String> =
        runCatching {
            val signature = RelayCrypto.signRequest(secret, body.toByteArray())
            val request = Request.Builder()
                .url("${room.senderUrl.trimEnd('/')}$path")
                .post(body.toRequestBody(JSON_CONTENT_TYPE.toMediaType()))
                .addHeader(RelayWorker.HEADER_SIGNATURE, signature)
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Timber.w("Relay %s rejected (HTTP %d): %s", path, response.code, text)
                    error("HTTP ${response.code}")
                }
                text
            }
        }

    private companion object {
        const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"
    }
}
