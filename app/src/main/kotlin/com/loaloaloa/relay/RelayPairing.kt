package com.loaloaloa.relay

import com.loaloaloa.data.model.RelayRoom
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The data a hub hands a spoke to join its room: the room id, the shared 256-bit secret, and the
 * Sender URL. Encoded into the pairing QR (and a copyable text code). The secret is the only thing
 * that grants decryption + send rights, so the QR is shown transiently and scanned locally — it
 * never travels over the network.
 *
 * Serialized as base64url(JSON) so the whole pairing is one compact, copy-paste-safe token that
 * also round-trips cleanly through a QR symbol.
 */
@Serializable
data class RelayPairing(
    val roomId: String,
    val roomSecret: String,
    val senderUrl: String,
)

/** Encode/decode the pairing token carried by the QR. Decoding never throws — null on garbage. */
object RelayPairingCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder: Base64.Decoder = Base64.getUrlDecoder()

    fun encode(pairing: RelayPairing): String =
        encoder.encodeToString(json.encodeToString(RelayPairing.serializer(), pairing).toByteArray())

    /**
     * The App-Link pairing URL the hub shows as QR + copyable text: "<senderUrl>/pair#<token>".
     * The token rides in the URL *fragment* so the shared secret never reaches the Sender. An
     * employee without the app who scans it with any camera lands on that /pair page (which offers
     * the APK); once the app is installed, opening the same link jumps straight into pairing.
     */
    fun link(pairing: RelayPairing): String {
        val base = pairing.senderUrl.trim().trimEnd('/')
        return "$base/pair#${encode(pairing)}"
    }

    /**
     * Decode a scanned/pasted token back to a pairing; null if malformed or not a relay token.
     * Accepts either a bare token or a full pairing URL ("https://…/pair#<token>") — the token lives
     * in the URL fragment, so we take whatever follows the last '#' (and a bare token, having no '#',
     * is used as-is).
     */
    fun decode(token: String): RelayPairing? = runCatching {
        val raw = token.trim()
        val payload = raw.substringAfterLast('#', raw)
        json.decodeFromString(RelayPairing.serializer(), decoder.decode(payload).decodeToString())
    }.getOrNull()
}

/** The [RelayRoom] this pairing configures on the joining device. */
fun RelayPairing.toRoom(): RelayRoom =
    RelayRoom(roomId = roomId, roomSecret = roomSecret, senderUrl = senderUrl)

/** The pairing token a hub publishes for its [RelayRoom]. */
fun RelayRoom.toPairing(): RelayPairing =
    RelayPairing(roomId = roomId, roomSecret = roomSecret, senderUrl = senderUrl)
