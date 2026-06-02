package com.loaloaloa.webhook

import com.google.common.truth.Truth.assertThat
import com.loaloaloa.data.model.TransactionModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Test

/** Pure mapping + JSON shape for the outbound webhook payload. */
class WebhookPayloadTest {

    private fun model(isIncome: Boolean) = TransactionModel(
        appId = "com.vcb.app",
        bankName = "Vietcombank",
        amount = 500_000L,
        isIncome = isIncome,
        rawText = "GD: +500.000 noi dung CK",
        timestamp = 1_700_000_000_000L,
    )

    @Test fun `income maps to type in`() {
        val payload = buildPayload(model(isIncome = true), "2024-01-15T10:30:00+07:00")
        assertThat(payload.type).isEqualTo("in")
    }

    @Test fun `outgoing maps to type out`() {
        val payload = buildPayload(model(isIncome = false), "2024-01-15T10:30:00+07:00")
        assertThat(payload.type).isEqualTo("out")
    }

    @Test fun `all fields are mapped from the model`() {
        val iso = "2024-01-15T10:30:00+07:00"
        val m = model(isIncome = true)
        val payload = buildPayload(m, iso)

        assertThat(payload.gateway).isEqualTo("LoaLoaLoa")
        assertThat(payload.bank).isEqualTo("Vietcombank")
        assertThat(payload.appId).isEqualTo("com.vcb.app")
        assertThat(payload.amount).isEqualTo(500_000L)
        assertThat(payload.content).isEqualTo("GD: +500.000 noi dung CK")
        assertThat(payload.timestamp).isEqualTo(iso)
    }

    @Test fun `toJson produces the expected shape`() {
        val iso = "2024-01-15T10:30:00+07:00"
        val payload = buildPayload(model(isIncome = false), iso)

        val obj: JsonObject = Json.parseToJsonElement(payload.toJson()).jsonObject

        assertThat(obj["gateway"]).isEqualTo(JsonPrimitive("LoaLoaLoa"))
        assertThat(obj["bank"]).isEqualTo(JsonPrimitive("Vietcombank"))
        assertThat(obj["appId"]).isEqualTo(JsonPrimitive("com.vcb.app"))
        assertThat(obj["type"]).isEqualTo(JsonPrimitive("out"))
        assertThat(obj["content"]).isEqualTo(JsonPrimitive("GD: +500.000 noi dung CK"))
        assertThat(obj["timestamp"]).isEqualTo(JsonPrimitive(iso))
        // amount must serialize as a JSON number, not a quoted string.
        val amount = obj["amount"]!!.jsonPrimitive
        assertThat(amount.isString).isFalse()
        assertThat(amount.longOrNull).isEqualTo(500_000L)
    }
}
