package com.loaloaloa.relay

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RelayCryptoTest {

    private val secret = RelayCrypto.decodeSecret(RelayCrypto.newRoomSecret())
    private val plaintext = """{"txId":"abc","bank":"Vietcombank","amount":123000,"type":"in"}"""
        .toByteArray()

    // ---- encrypt / decrypt round-trip ----

    @Test fun `encrypt then decrypt recovers plaintext`() {
        val blob = RelayCrypto.encrypt(secret, plaintext)
        assertThat(RelayCrypto.decrypt(secret, blob)).isEqualTo(plaintext)
    }

    @Test fun `each encryption uses a fresh iv so ciphertext differs`() {
        val a = RelayCrypto.encrypt(secret, plaintext)
        val b = RelayCrypto.encrypt(secret, plaintext)
        assertThat(a).isNotEqualTo(b)
        // ...but both decrypt back to the same plaintext.
        assertThat(RelayCrypto.decrypt(secret, a)).isEqualTo(plaintext)
        assertThat(RelayCrypto.decrypt(secret, b)).isEqualTo(plaintext)
    }

    // ---- decrypt rejects bad input (never throws) ----

    @Test fun `decrypt with wrong secret returns null`() {
        val blob = RelayCrypto.encrypt(secret, plaintext)
        val other = RelayCrypto.decodeSecret(RelayCrypto.newRoomSecret())
        assertThat(RelayCrypto.decrypt(other, blob)).isNull()
    }

    @Test fun `decrypt of tampered blob returns null`() {
        val blob = RelayCrypto.encrypt(secret, plaintext)
        // Flip the first base64url char — it encodes the leading IV bits (always
        // significant, no trailing-bit ambiguity), so GCM authentication must fail.
        val tampered = (if (blob.first() == 'A') 'B' else 'A') + blob.drop(1)
        assertThat(RelayCrypto.decrypt(secret, tampered)).isNull()
    }

    @Test fun `decrypt of malformed input returns null`() {
        assertThat(RelayCrypto.decrypt(secret, "not base64 !!!")).isNull()
        assertThat(RelayCrypto.decrypt(secret, "")).isNull()
        assertThat(RelayCrypto.decrypt(secret, "AAAA")).isNull() // shorter than the IV
    }

    // ---- request signing ----

    @Test fun `sign then verify accepts a matching body`() {
        val body = "hello".toByteArray()
        val sig = RelayCrypto.signRequest(secret, body)
        assertThat(RelayCrypto.verifyRequest(secret, body, sig)).isTrue()
    }

    @Test fun `verify rejects a tampered body`() {
        val sig = RelayCrypto.signRequest(secret, "hello".toByteArray())
        assertThat(RelayCrypto.verifyRequest(secret, "hellp".toByteArray(), sig)).isFalse()
    }

    @Test fun `verify rejects a wrong secret`() {
        val body = "hello".toByteArray()
        val sig = RelayCrypto.signRequest(secret, body)
        val other = RelayCrypto.decodeSecret(RelayCrypto.newRoomSecret())
        assertThat(RelayCrypto.verifyRequest(other, body, sig)).isFalse()
    }

    @Test fun `verify rejects a malformed signature`() {
        assertThat(RelayCrypto.verifyRequest(secret, "hello".toByteArray(), "!!!")).isFalse()
    }

    // ---- room secret generation ----

    @Test fun `new room secret is 256 bits and unique per call`() {
        val a = RelayCrypto.newRoomSecret()
        val b = RelayCrypto.newRoomSecret()
        assertThat(a).isNotEqualTo(b)
        assertThat(RelayCrypto.decodeSecret(a)).hasLength(32)
    }
}
