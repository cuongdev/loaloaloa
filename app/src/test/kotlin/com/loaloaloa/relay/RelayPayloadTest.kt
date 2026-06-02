package com.loaloaloa.relay

import com.google.common.truth.Truth.assertThat
import com.loaloaloa.data.model.TransactionModel
import org.junit.Test

class RelayPayloadTest {

    private fun model(
        amount: Long = 500_000,
        isIncome: Boolean = true,
        rawText: String = "+500.000đ",
        timestamp: Long = 1_000,
    ) = TransactionModel(
        appId = "com.VCB",
        bankName = "Vietcombank",
        amount = amount,
        isIncome = isIncome,
        rawText = rawText,
        timestamp = timestamp,
    )

    @Test fun `model to payload to model round-trips faithfully`() {
        val original = model()
        val recovered = RelayJson.decode(RelayJson.encode(original.toRelayPayload()))!!.toModel()
        assertThat(recovered).isEqualTo(original)
    }

    @Test fun `income maps to type in and outgoing to type out`() {
        assertThat(model(isIncome = true).toRelayPayload().type).isEqualTo("in")
        assertThat(model(isIncome = false).toRelayPayload().type).isEqualTo("out")
        // ...and the direction survives the round-trip.
        assertThat(RelayJson.decode(RelayJson.encode(model(isIncome = false).toRelayPayload()))!!.toModel().isIncome)
            .isFalse()
    }

    @Test fun `txId is deterministic for the same transaction`() {
        assertThat(relayTxId(model())).isEqualTo(relayTxId(model()))
    }

    @Test fun `txId differs when amount direction time or text differ`() {
        val base = relayTxId(model())
        assertThat(relayTxId(model(amount = 600_000))).isNotEqualTo(base)
        assertThat(relayTxId(model(isIncome = false))).isNotEqualTo(base)
        assertThat(relayTxId(model(timestamp = 2_000))).isNotEqualTo(base)
        assertThat(relayTxId(model(rawText = "+500.001đ"))).isNotEqualTo(base)
    }

    @Test fun `decode of malformed json returns null`() {
        assertThat(RelayJson.decode("not json")).isNull()
        assertThat(RelayJson.decode("")).isNull()
        assertThat(RelayJson.decode("""{"txId":"x"}""")).isNull() // missing required fields
    }
}
