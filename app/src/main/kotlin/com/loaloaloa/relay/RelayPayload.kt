package com.loaloaloa.relay

import com.loaloaloa.data.model.TransactionModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The plaintext a hub relays to its spokes for one detected transaction. This is the JSON that
 * [RelayCrypto.encrypt] wraps end-to-end, so neither the Cloudflare Sender nor Google (FCM) ever
 * sees the money data — they only carry ciphertext.
 *
 * Shape mirrors a [TransactionModel] closely enough to reconstruct one faithfully on the spoke
 * (so the spoke's history, widget, and speech look identical to the hub's), plus a [txId] the
 * spoke dedupes on: FCM may redeliver the same message, and the hub may retry, so a stable id per
 * transaction lets the spoke announce each exactly once.
 *
 * @param txId    stable per-transaction id (see [relayTxId]); the spoke's dedupe key.
 * @param appId   source package name (notification) or provider id (API) on the hub.
 * @param bank    resolved bank/wallet name.
 * @param amount  absolute VND amount (always >= 0).
 * @param type    `"in"` for money received, `"out"` for money sent.
 * @param content original notification/transaction text.
 * @param ts      epoch millis the transaction was observed on the hub.
 */
@Serializable
data class RelayPayload(
    val txId: String,
    val appId: String,
    val bank: String,
    val amount: Long,
    val type: String,
    val content: String,
    val ts: Long,
)

/**
 * Deterministic id for a transaction, so the same event relayed twice (FCM redelivery or a hub
 * retry) yields the same [RelayPayload.txId] and the spoke drops the repeat. [String.hashCode] is
 * specified by the JVM (a fixed polynomial), so it is stable across processes and devices — good
 * enough as a dedupe discriminator alongside the exact timestamp, amount, and direction.
 */
fun relayTxId(model: TransactionModel): String =
    "${model.timestamp}-${model.amount}-${if (model.isIncome) "i" else "o"}-${model.rawText.hashCode()}"

/** Pure mapping from a detected [TransactionModel] to the relay plaintext. */
fun TransactionModel.toRelayPayload(): RelayPayload = RelayPayload(
    txId = relayTxId(this),
    appId = appId,
    bank = bankName,
    amount = amount,
    type = if (isIncome) "in" else "out",
    content = rawText,
    ts = timestamp,
)

/** Pure mapping back to a [TransactionModel] for the spoke's ingest pipeline. */
fun RelayPayload.toModel(): TransactionModel = TransactionModel(
    appId = appId,
    bankName = bank,
    amount = amount,
    isIncome = type == "in",
    rawText = content,
    timestamp = ts,
)

/**
 * The cleartext request body a hub POSTs to the Sender. Carries only the [roomId] (so the Sender
 * knows which room's spoke tokens to fan out to and which registered mac key to verify against)
 * and the already-E2E-encrypted [blob] — never any plaintext. The request is HMAC-signed
 * separately (see [RelayCrypto.signRequest]); the Sender verifies that signature with the mac
 * subkey registered at pairing, which is independent of the encryption key, so it authenticates
 * senders without ever being able to decrypt the [blob].
 */
@Serializable
data class RelayEnvelope(
    val roomId: String,
    val blob: String,
)

/**
 * The cleartext body a device POSTs to the Sender's `/register` endpoint. Carries the room's
 * [macKey] (base64url of the MAC subkey — see [RelayCrypto.macKeyB64]) so the Sender can verify this
 * and future signed requests, and optionally a spoke's FCM [token] to add to the room's fan-out set:
 *
 *  - a **hub** registers once at room creation with [token] = null, just to publish the mac key
 *    (trust-on-first-use: the Sender pins it to the [roomId]);
 *  - a **spoke** registers its current FCM [token] so the Sender can push relayed blobs to it.
 *
 * The request is HMAC-signed with the same mac key (see [RelayCrypto.signRequest]); on first contact
 * the Sender stores [macKey] then verifies the signature against it (self-certifying), and on later
 * contacts verifies against the pinned key — so only a holder of the room secret can register. The
 * mac key is independent of the encryption key, so this never weakens E2E confidentiality.
 *
 * [label] is a human-readable device name (e.g. "Realme CPH2671") a spoke sends so the hub's paired-
 * devices list is recognizable; null/blank for a hub provision (which carries no token).
 */
@Serializable
data class RelayRegistration(
    val roomId: String,
    val macKey: String,
    val token: String? = null,
    val label: String? = null,
)

/**
 * One spoke device registered in a room, as returned by the Sender's `/devices` list. [token] is the
 * device's FCM token (the revoke handle); [label] is the device name it registered with (may be
 * blank for devices paired before labels existed); [ts] is the epoch-**seconds** it last (re)paired.
 */
@Serializable
data class RelayDevice(
    val token: String,
    val label: String = "",
    val ts: Long = 0,
)

/** The cleartext, HMAC-signed body for the Sender's `/devices` list request. */
@Serializable
data class RelayDevicesRequest(val roomId: String)

/**
 * One pair/unpair audit entry in a room, as returned by the Sender's `/devices` list (newest first).
 * [type] is "pair" or "unpair"; [label] the device name; [ts] the epoch-**seconds** it happened.
 */
@Serializable
data class RelayEvent(
    val type: String = "",
    val token: String = "",
    val label: String = "",
    val ts: Long = 0,
)

/** The Sender's `/devices` response: the room's paired spoke devices + the pair/unpair audit log. */
@Serializable
data class RelayDevicesResponse(
    val ok: Boolean = false,
    val devices: List<RelayDevice> = emptyList(),
    val events: List<RelayEvent> = emptyList(),
)

/** The cleartext, HMAC-signed body for the Sender's `/revoke` (drop one spoke device by token). */
@Serializable
data class RelayRevokeRequest(val roomId: String, val token: String)

/** JSON codec for the relay plaintext (the bytes [RelayCrypto] encrypts / the spoke decrypts). */
object RelayJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(payload: RelayPayload): String = json.encodeToString(RelayPayload.serializer(), payload)

    /** Decode a relay JSON string; null on malformed input (never throws). */
    fun decode(text: String): RelayPayload? =
        runCatching { json.decodeFromString(RelayPayload.serializer(), text) }.getOrNull()

    /** Serialize the hub→Sender request body (these exact bytes are what gets HMAC-signed). */
    fun encodeEnvelope(envelope: RelayEnvelope): String =
        json.encodeToString(RelayEnvelope.serializer(), envelope)

    /** Serialize a `/register` request body (these exact bytes are what gets HMAC-signed). */
    fun encodeRegistration(registration: RelayRegistration): String =
        json.encodeToString(RelayRegistration.serializer(), registration)

    /** Serialize a `/devices` request body (these exact bytes are what gets HMAC-signed). */
    fun encodeDevicesRequest(request: RelayDevicesRequest): String =
        json.encodeToString(RelayDevicesRequest.serializer(), request)

    /** Serialize a `/revoke` request body (these exact bytes are what gets HMAC-signed). */
    fun encodeRevoke(request: RelayRevokeRequest): String =
        json.encodeToString(RelayRevokeRequest.serializer(), request)

    /** Decode a `/devices` response; null on malformed input (never throws). */
    fun decodeDevicesResponse(text: String): RelayDevicesResponse? =
        runCatching { json.decodeFromString(RelayDevicesResponse.serializer(), text) }.getOrNull()
}
