package com.loaloaloa.relay

import com.loaloaloa.data.model.RelayRole
import com.loaloaloa.data.model.TransactionModel
import com.loaloaloa.data.repository.UserSettingsRepository
import com.loaloaloa.source.notification.DedupeGate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Spoke-side receiver for the device relay. Push-driven: the FCM service
 * ([RelayFirebaseMessagingService]) hands each relay message's ciphertext to [receive], which
 * decrypts it with the paired room secret, parses the [RelayPayload], dedupes, and returns a
 * [TransactionModel] for the FCM service to feed into [com.loaloaloa.ingest.TransactionIngestor]
 * — so a spoke announces relayed transactions exactly as if it had detected them locally, with no
 * bank login on the device.
 *
 * Unlike the notification listener / API poller this is NOT a long-lived [TransactionSource] with a
 * collector: FCM may start the process fresh for a single message, before any collector subscribes,
 * so the receiver returns the model synchronously and the FCM callback ingests it inline. Decoupling
 * keeps the impure boundary (settings + clock) here while the pure pieces ([RelayCrypto], [RelayJson],
 * [toModel]) stay unit-tested.
 *
 * Security gate: [receive] only acts when this device is a [RelayRole.SPOKE] paired with a room. A
 * blob that fails to decrypt (tampered, wrong key) or parse returns null — the spoke never announces
 * garbage. Decryption keeps the plaintext end-to-end: the Sender and FCM only ever moved ciphertext.
 */
@Singleton
class RelayReceiverSource @Inject constructor(
    private val userSettingsRepository: UserSettingsRepository,
) {

    // FCM may redeliver a message, and the hub may retry on transient failure; a generous window on
    // the deterministic txId collapses those into a single announcement. The gate is stateful and
    // this class is a @Singleton, so dedupe persists for the life of the process.
    private val dedupe = DedupeGate(windowMillis = RELAY_DEDUPE_WINDOW_MILLIS)

    /**
     * Process one relay push. Returns the decoded transaction to announce, or null when the device
     * isn't a paired spoke, the blob can't be decrypted/parsed, or it's a duplicate within the
     * window. Never throws: a bad blob must not crash the FCM callback.
     */
    suspend fun receive(blob: String, nowMillis: Long = System.currentTimeMillis()): TransactionModel? {
        val settings = userSettingsRepository.settings.first()
        if (settings.relayRole != RelayRole.SPOKE) return null
        val room = settings.relayRoom
        if (room.roomSecret.isBlank()) return null

        val secret = runCatching { RelayCrypto.decodeSecret(room.roomSecret) }.getOrNull() ?: return null
        val plaintext = RelayCrypto.decrypt(secret, blob)
        if (plaintext == null) {
            Timber.w("Relay: dropping undecryptable push (tampered or wrong room secret)")
            return null
        }
        val payload = RelayJson.decode(plaintext.decodeToString())
        if (payload == null) {
            Timber.w("Relay: dropping unparseable payload")
            return null
        }
        if (dedupe.isDuplicate(payload.txId, nowMillis)) return null

        return payload.toModel()
    }

    private companion object {
        const val RELAY_DEDUPE_WINDOW_MILLIS: Long = 5 * 60 * 1000
    }
}
