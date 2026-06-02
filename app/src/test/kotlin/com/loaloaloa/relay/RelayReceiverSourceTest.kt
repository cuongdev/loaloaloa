package com.loaloaloa.relay

import com.google.common.truth.Truth.assertThat
import com.loaloaloa.data.model.RelayRole
import com.loaloaloa.data.model.RelayRoom
import com.loaloaloa.data.model.TransactionModel
import com.loaloaloa.data.model.UserSettings
import com.loaloaloa.ui.fake.FakeUserSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RelayReceiverSourceTest {

    private val secret = RelayCrypto.newRoomSecret()

    private fun model() = TransactionModel(
        appId = "com.VCB",
        bankName = "Vietcombank",
        amount = 500_000,
        isIncome = true,
        rawText = "+500.000đ",
        timestamp = 1_000,
    )

    private fun pairedSpoke() = UserSettings(
        relayRole = RelayRole.SPOKE,
        relayRoom = RelayRoom(roomId = "r1", roomSecret = secret, senderUrl = "https://sender"),
    )

    /** Encrypt a transaction's relay payload exactly as the hub would, under [key]. */
    private fun blobFor(model: TransactionModel, key: String = secret): String =
        RelayCrypto.encrypt(RelayCrypto.decodeSecret(key), RelayJson.encode(model.toRelayPayload()).toByteArray())

    @Test fun `paired spoke decrypts and returns the relayed transaction`() = runTest {
        val source = RelayReceiverSource(FakeUserSettingsRepository(pairedSpoke()))
        assertThat(source.receive(blobFor(model()), nowMillis = 0)).isEqualTo(model())
    }

    @Test fun `non-spoke role drops the push`() = runTest {
        val hub = pairedSpoke().copy(relayRole = RelayRole.HUB)
        val source = RelayReceiverSource(FakeUserSettingsRepository(hub))
        assertThat(source.receive(blobFor(model()), nowMillis = 0)).isNull()
    }

    @Test fun `unpaired spoke with blank secret drops the push`() = runTest {
        val unpaired = UserSettings(relayRole = RelayRole.SPOKE, relayRoom = RelayRoom())
        val source = RelayReceiverSource(FakeUserSettingsRepository(unpaired))
        // Encrypt under some real key — it still can't be accepted with no paired secret.
        assertThat(source.receive(blobFor(model()), nowMillis = 0)).isNull()
    }

    @Test fun `tampered blob is dropped`() = runTest {
        val source = RelayReceiverSource(FakeUserSettingsRepository(pairedSpoke()))
        val blob = blobFor(model())
        val tampered = (if (blob.first() == 'A') 'B' else 'A') + blob.drop(1)
        assertThat(source.receive(tampered, nowMillis = 0)).isNull()
    }

    @Test fun `blob from a different room secret is dropped`() = runTest {
        val source = RelayReceiverSource(FakeUserSettingsRepository(pairedSpoke()))
        assertThat(source.receive(blobFor(model(), key = RelayCrypto.newRoomSecret()), nowMillis = 0)).isNull()
    }

    @Test fun `duplicate txId within the window is announced once`() = runTest {
        val source = RelayReceiverSource(FakeUserSettingsRepository(pairedSpoke()))
        val first = source.receive(blobFor(model()), nowMillis = 0)
        // A fresh encryption of the same transaction → different ciphertext, same txId.
        val second = source.receive(blobFor(model()), nowMillis = 1_000)
        assertThat(first).isEqualTo(model())
        assertThat(second).isNull()
    }
}
