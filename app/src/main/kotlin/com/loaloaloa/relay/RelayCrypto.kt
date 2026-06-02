package com.loaloaloa.relay

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Pure cryptographic primitives for the device relay. A shop "room" shares a
 * single 256-bit `roomSecret`; from it two independent subkeys are derived via
 * labeled HMAC-SHA256 (HKDF-style expand):
 *
 *  - an **AES-256-GCM** key for end-to-end payload encryption, so neither the
 *    Cloudflare Sender nor Google (FCM) ever sees plaintext money data;
 *  - an **HMAC** key that authenticates hub/spoke → Sender requests.
 *
 * No Android types are used: AES-GCM, HMAC, [SecureRandom], and [java.util.Base64]
 * are all available from API 26 (the app's minSdk) and on the JVM, so this object
 * is exercised directly by unit tests. Decryption never throws — a tampered,
 * wrong-key, or malformed blob returns null so the caller drops it silently rather
 * than announcing garbage.
 */
object RelayCrypto {
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12
    private const val ENC_LABEL = "tingting-relay-enc-v1"
    private const val MAC_LABEL = "tingting-relay-mac-v1"

    private val b64Encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val b64Decoder: Base64.Decoder = Base64.getUrlDecoder()

    /** A labeled 32-byte subkey = HMAC-SHA256(roomSecret, label). */
    private fun subKey(roomSecret: ByteArray, label: String): ByteArray =
        hmac(roomSecret, label.toByteArray(Charsets.UTF_8))

    private fun encKey(roomSecret: ByteArray) = subKey(roomSecret, ENC_LABEL)
    private fun macKey(roomSecret: ByteArray) = subKey(roomSecret, MAC_LABEL)

    /** Raw HMAC-SHA256(key, data). */
    fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }

    /** Base64url HMAC of [body] under the room's MAC subkey — the request signature. */
    fun signRequest(roomSecret: ByteArray, body: ByteArray): String =
        b64Encoder.encodeToString(hmac(macKey(roomSecret), body))

    /**
     * Base64url of the room's MAC subkey — the only key a device hands the Sender (at registration),
     * so the Sender can verify hub/spoke request signatures. It is independent of the AES encryption
     * key, so sharing it never lets the Sender decrypt a relayed blob; E2E confidentiality holds.
     */
    fun macKeyB64(roomSecret: ByteArray): String =
        b64Encoder.encodeToString(macKey(roomSecret))

    /** Constant-time verify of [signature] (base64url) against [body]; false on any error. */
    fun verifyRequest(roomSecret: ByteArray, body: ByteArray, signature: String): Boolean =
        runCatching {
            MessageDigest.isEqual(hmac(macKey(roomSecret), body), b64Decoder.decode(signature))
        }.getOrDefault(false)

    /** AES-256-GCM encrypt [plaintext]; returns base64url(iv ‖ ciphertext+tag). */
    fun encrypt(
        roomSecret: ByteArray,
        plaintext: ByteArray,
        rng: SecureRandom = SecureRandom(),
    ): String {
        val iv = ByteArray(IV_BYTES).also(rng::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey(roomSecret), "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        return b64Encoder.encodeToString(iv + cipher.doFinal(plaintext))
    }

    /** Decrypt an [encrypt] blob; null on any tamper/wrong-key/format error (never throws). */
    fun decrypt(roomSecret: ByteArray, blob: String): ByteArray? = runCatching {
        val raw = b64Decoder.decode(blob)
        require(raw.size > IV_BYTES)
        val iv = raw.copyOfRange(0, IV_BYTES)
        val body = raw.copyOfRange(IV_BYTES, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey(roomSecret), "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        cipher.doFinal(body)
    }.getOrNull()

    /** Generate a fresh 256-bit room secret (base64url) for a new room. */
    fun newRoomSecret(rng: SecureRandom = SecureRandom()): String =
        b64Encoder.encodeToString(ByteArray(32).also(rng::nextBytes))

    /** Decode a base64url room secret back to raw bytes. */
    fun decodeSecret(secret: String): ByteArray = b64Decoder.decode(secret)
}
