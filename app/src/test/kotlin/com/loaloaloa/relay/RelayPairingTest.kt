package com.loaloaloa.relay

import com.google.common.truth.Truth.assertThat
import com.loaloaloa.data.model.RelayRoom
import org.junit.Test

class RelayPairingTest {

    private val pairing = RelayPairing(
        roomId = "room-123",
        roomSecret = RelayCrypto.newRoomSecret(),
        senderUrl = "https://relay.example.workers.dev",
    )

    @Test fun `encode then decode round-trips`() {
        assertThat(RelayPairingCodec.decode(RelayPairingCodec.encode(pairing))).isEqualTo(pairing)
    }

    @Test fun `decode tolerates surrounding whitespace`() {
        val token = RelayPairingCodec.encode(pairing)
        assertThat(RelayPairingCodec.decode("  $token\n")).isEqualTo(pairing)
    }

    @Test fun `decode of garbage returns null`() {
        assertThat(RelayPairingCodec.decode("not a token")).isNull()
        assertThat(RelayPairingCodec.decode("")).isNull()
    }

    @Test fun `room to pairing to room is identity`() {
        val room = RelayRoom(roomId = "r", roomSecret = "s", senderUrl = "https://u")
        assertThat(room.toPairing().toRoom()).isEqualTo(room)
    }
}
