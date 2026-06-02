package com.loaloaloa.relay

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.loaloaloa.data.model.RelayRoom
import com.loaloaloa.data.model.TransactionModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Hub side of the relay. Turns a detected [TransactionModel] into an E2E-encrypted, HMAC-signed
 * request and enqueues a [RelayWorker] to POST it to the room's Sender, which fans it out to the
 * spokes via FCM. The encryption happens here so WorkManager only ever persists ciphertext + a
 * signature (never plaintext money data or the room secret); the worker is a dumb POSTer, exactly
 * like [com.loaloaloa.webhook.WebhookSender] / WebhookWorker.
 *
 * Each transaction is its own non-unique one-time request so bursts all deliver; WorkManager owns
 * the network constraint and exponential-backoff retry.
 */
@Singleton
class RelaySender @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Encrypt, sign, and enqueue [model] for relay to [room]'s spokes. No-op if [room] is unpaired. */
    fun relay(model: TransactionModel, room: RelayRoom) {
        if (room.roomId.isBlank() || room.roomSecret.isBlank() || room.senderUrl.isBlank()) return
        val secret = runCatching { RelayCrypto.decodeSecret(room.roomSecret) }.getOrNull() ?: run {
            Timber.w("Relay: room secret is not valid base64url; skipping push")
            return
        }

        val blob = RelayCrypto.encrypt(secret, RelayJson.encode(model.toRelayPayload()).toByteArray())
        val body = RelayJson.encodeEnvelope(RelayEnvelope(roomId = room.roomId, blob = blob))
        val signature = RelayCrypto.signRequest(secret, body.toByteArray())

        val data = Data.Builder()
            .putString(RelayWorker.KEY_URL, sendUrl(room.senderUrl))
            .putString(RelayWorker.KEY_BODY, body)
            .putString(RelayWorker.KEY_SIGNATURE, signature)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<RelayWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    /** Resolve the room's Sender base URL to the fan-out endpoint, tolerating a trailing slash. */
    private fun sendUrl(base: String): String = "${base.trimEnd('/')}/send"

    private companion object {
        const val TAG = "loaloaloa_relay"
        const val BACKOFF_SECONDS = 10L
    }
}
