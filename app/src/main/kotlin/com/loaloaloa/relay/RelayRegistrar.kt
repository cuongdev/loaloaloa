package com.loaloaloa.relay

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.loaloaloa.data.model.RelayRegisterState
import com.loaloaloa.data.model.RelayRoom
import com.loaloaloa.data.repository.UserSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Pure mapping from FCM availability + token presence to the persisted [RelayRegisterState].
 * Extracted so the decision is unit-testable without WorkManager/Firebase.
 */
fun relayRegisterStateFor(fcmAvailable: Boolean, token: String?): RelayRegisterState = when {
    !fcmAvailable -> RelayRegisterState.NO_FCM
    token.isNullOrBlank() -> RelayRegisterState.NO_FCM
    else -> RelayRegisterState.REGISTERED
}

/**
 * Registers a device with its room's Sender (Cloudflare Worker) so the relay can route to it:
 *
 *  - [provision] — a **hub** publishes the room's mac key once at room creation, so the Sender can
 *    verify the room's signed requests (trust-on-first-use, pinned to the room id);
 *  - [registerCurrentToken] / [registerToken] — a **spoke** registers its FCM token so the Sender
 *    knows where to fan out relayed blobs. Called when pairing and again from
 *    [RelayFirebaseMessagingService.onNewToken] whenever FCM rotates the token.
 *
 * An interface so the relay settings VM stays free of WorkManager/Firebase plumbing (and unit-test
 * friendly); [WorkManagerRelayRegistrar] is the production implementation.
 */
interface RelayRegistrar {
    fun provision(room: RelayRoom)
    fun registerToken(room: RelayRoom, token: String)
    suspend fun registerCurrentToken(room: RelayRoom)
}

/**
 * Production [RelayRegistrar]: builds a signed [RelayRegistration] body and enqueues the same dumb
 * POSTer ([RelayWorker]) at the room's `/register` endpoint — WorkManager owns the network
 * constraint and retry, exactly like [RelaySender].
 *
 * Acquiring the FCM token needs a configured FirebaseApp; with no `google-services.json` yet,
 * [FirebaseMessaging.getInstance] throws, so token lookup is guarded and simply no-ops (the relay
 * stays inactive until Firebase is set up — see relay-worker/README).
 */
@Singleton
class WorkManagerRelayRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenProvider: FcmTokenProvider,
    private val settingsRepository: UserSettingsRepository,
) : RelayRegistrar {

    override fun provision(room: RelayRoom) = enqueue(room, token = null)

    override fun registerToken(room: RelayRoom, token: String) = enqueue(room, token = token)

    override suspend fun registerCurrentToken(room: RelayRoom) {
        val available = tokenProvider.isFcmAvailable()
        val token = if (available) tokenProvider.currentToken() else null
        val state = relayRegisterStateFor(available, token)
        settingsRepository.setRelayRegisterState(state)
        if (state == RelayRegisterState.REGISTERED && token != null) {
            registerToken(room, token)
        } else {
            Timber.w("Relay: spoke registration unavailable (state=%s)", state)
        }
    }

    /** Build the signed [RelayRegistration] body and enqueue a [RelayWorker] POST to `/register`. */
    private fun enqueue(room: RelayRoom, token: String?) {
        if (room.roomId.isBlank() || room.roomSecret.isBlank() || room.senderUrl.isBlank()) return
        val secret = runCatching { RelayCrypto.decodeSecret(room.roomSecret) }.getOrNull() ?: run {
            Timber.w("Relay: room secret is not valid base64url; skipping registration")
            return
        }

        val body = RelayJson.encodeRegistration(
            RelayRegistration(
                roomId = room.roomId,
                macKey = RelayCrypto.macKeyB64(secret),
                token = token,
                // A spoke (token present) labels itself with its device name so the hub's paired list
                // is recognizable; a hub provision (token == null) carries no label.
                label = if (token != null) deviceLabel() else null,
            ),
        )
        val signature = RelayCrypto.signRequest(secret, body.toByteArray())

        val data = Data.Builder()
            .putString(RelayWorker.KEY_URL, registerUrl(room.senderUrl))
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

        // Keep only the latest registration per room+token in flight: a rapid re-pair or token churn
        // should supersede, not stack. The unique name folds in the token so a hub provision and a
        // spoke token-register for the same room don't cancel each other.
        val uniqueName = "$TAG:${room.roomId}:${token ?: "provision"}"
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
    }

    /** Resolve the room's Sender base URL to the registration endpoint, tolerating a trailing slash. */
    private fun registerUrl(base: String): String = "${base.trimEnd('/')}/register"

    /** A human-readable name for this device (manufacturer + model), capped, for the hub's list. */
    private fun deviceLabel(): String {
        val manufacturer = Build.MANUFACTURER?.replaceFirstChar { it.uppercase() }.orEmpty()
        val model = Build.MODEL.orEmpty()
        return listOf(manufacturer, model)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
            .take(60)
            .ifBlank { "Máy nhân viên" }
    }

    private companion object {
        const val TAG = "loaloaloa_relay_register"
        const val BACKOFF_SECONDS = 10L
    }
}
