package com.loaloaloa.relay

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.loaloaloa.data.model.RelayRegisterState
import com.loaloaloa.data.model.RelayRole
import com.loaloaloa.data.repository.UserSettingsRepository
import com.loaloaloa.ingest.TransactionIngestor
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * Spoke-side entry point for the device relay. FCM wakes this service — even on a dozing or
 * OEM-killed personal phone — with each relayed transaction as a data message; we decrypt it with
 * the paired room secret and feed it into the same [TransactionIngestor] a locally-detected
 * transaction would use, so the spoke announces it on the loa with no bank login on the device.
 *
 * The work runs inline ([runBlocking]) inside FCM's delivery window: the process may have been
 * started fresh for this single message, so there is no long-lived collector to hand off to — that
 * is why [RelayReceiverSource] returns a model synchronously instead of emitting on a flow. The blob
 * arrives under the `blob` data key as base64url AES-GCM ciphertext (the Sender and Google only ever
 * moved ciphertext; see [RelayCrypto]). A null/garbage blob is dropped silently.
 *
 * [onNewToken] re-registers the rotated FCM token with the room's Sender when this device is a
 * spoke, so fan-out keeps reaching it after FCM refreshes the token.
 *
 * Declared in the manifest with the `com.google.firebase.MESSAGING_EVENT` intent-filter. With no
 * `google-services.json`, FCM never initializes and this service is simply never invoked — the app
 * builds and runs with the relay inactive (see relay-worker/README).
 */
@AndroidEntryPoint
class RelayFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var receiverSource: RelayReceiverSource
    @Inject lateinit var ingestor: TransactionIngestor
    @Inject lateinit var registrar: RelayRegistrar
    @Inject lateinit var userSettingsRepository: UserSettingsRepository

    override fun onMessageReceived(message: RemoteMessage) {
        val blob = message.data[KEY_BLOB]
        if (blob.isNullOrBlank()) {
            Timber.w("Relay: FCM message with no '%s' data; ignoring", KEY_BLOB)
            return
        }
        runBlocking {
            runCatching {
                receiverSource.receive(blob)?.let { ingestor.ingest(it) }
            }.onFailure { Timber.w(it, "Relay: failed to handle FCM push") }
        }
    }

    override fun onNewToken(token: String) {
        runBlocking {
            runCatching {
                val settings = userSettingsRepository.settings.first()
                if (settings.relayRole == RelayRole.SPOKE) {
                    registrar.registerToken(settings.relayRoom, token)
                    userSettingsRepository.setRelayRegisterState(RelayRegisterState.REGISTERED)
                }
            }.onFailure { Timber.w(it, "Relay: failed to register rotated FCM token") }
        }
    }

    private companion object {
        const val KEY_BLOB = "blob"
    }
}
