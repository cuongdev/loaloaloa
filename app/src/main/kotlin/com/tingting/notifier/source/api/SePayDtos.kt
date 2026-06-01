package com.tingting.notifier.source.api

import com.google.gson.annotations.SerializedName

/**
 * Gson DTOs for SePay's `GET userapi/transactions/list` response. Modeled
 * tolerantly: every field is nullable and amounts arrive as STRINGS, so a partial
 * or unexpected row deserializes without throwing. Normalization into the app's
 * [com.tingting.notifier.data.model.TransactionModel] is done by [SePayNormalizer].
 *
 * [transactions] is nullable because Gson honors an explicit `"transactions": null`
 * in the payload (bypassing the Kotlin default), so callers must guard before
 * iterating.
 */
data class SePayListResponse(
    @SerializedName("status") val status: Int? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("transactions") val transactions: List<SePayTransactionDto>? = emptyList(),
)

data class SePayTransactionDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("bank_brand_name") val bankBrandName: String? = null,
    @SerializedName("account_number") val accountNumber: String? = null,
    @SerializedName("transaction_date") val transactionDate: String? = null,
    @SerializedName("amount_in") val amountIn: String? = null,
    @SerializedName("amount_out") val amountOut: String? = null,
    @SerializedName("transaction_content") val transactionContent: String? = null,
    @SerializedName("reference_number") val referenceNumber: String? = null,
)
