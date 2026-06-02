package com.loaloaloa.webhook

import com.google.common.truth.Truth.assertThat
import com.loaloaloa.data.model.TransactionModel
import com.loaloaloa.data.model.WebhookConfig
import com.loaloaloa.data.model.WebhookType
import java.net.URLDecoder
import org.junit.Test

/** Per-type outbound request building, focused on the Google Form (form-urlencoded) branch. */
class WebhookRequestTest {

    private val model = TransactionModel(
        appId = "com.mbmobile",
        bankName = "MB Bank",
        amount = 500_000L,
        isIncome = true,
        rawText = "Thanh toan don hang",
        timestamp = 1_700_000_000_000L,
    )

    private val iso = "2024-01-15T10:30:00+07:00"

    private fun googleForm() = WebhookConfig(
        type = WebhookType.GOOGLE_FORM,
        url = "https://docs.google.com/forms/d/e/ABC/formResponse",
        formAmountEntry = "entry.1",
        formBankEntry = "entry.2",
        formTimeEntry = "entry.3",
        formNoteEntry = "entry.4",
    )

    /** Decode an `application/x-www-form-urlencoded` body into a key→value map. */
    private fun decode(body: String): Map<String, String> =
        body.split("&").associate {
            val (k, v) = it.split("=", limit = 2)
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }

    @Test fun `google form maps fields onto entry ids as form-urlencoded`() {
        val req = buildWebhookRequest(googleForm(), model, iso)!!

        assertThat(req.url).isEqualTo("https://docs.google.com/forms/d/e/ABC/formResponse")
        assertThat(req.contentType).isEqualTo(FORM_CONTENT_TYPE)
        assertThat(req.secret).isEmpty()

        val fields = decode(req.body)
        assertThat(fields).containsExactly(
            "entry.1", "500000",
            "entry.2", "MB Bank",
            "entry.3", iso,
            "entry.4", "Thanh toan don hang",
        )
    }

    @Test fun `google form omits unmapped entries`() {
        val config = googleForm().copy(formBankEntry = "", formTimeEntry = "", formNoteEntry = "")
        val fields = decode(buildWebhookRequest(config, model, iso)!!.body)
        assertThat(fields.keys).containsExactly("entry.1")
    }

    @Test fun `google form with blank url is skipped`() {
        assertThat(buildWebhookRequest(googleForm().copy(url = ""), model, iso)).isNull()
    }

    @Test fun `google form with no entry ids is skipped`() {
        val config = googleForm().copy(
            formAmountEntry = "",
            formBankEntry = "",
            formTimeEntry = "",
            formNoteEntry = "",
        )
        assertThat(buildWebhookRequest(config, model, iso)).isNull()
    }
}
