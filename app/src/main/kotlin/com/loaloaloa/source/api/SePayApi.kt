package com.loaloaloa.source.api

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Retrofit binding for SePay's recent-transactions endpoint. The base URL and
 * bearer token come from the user's [com.loaloaloa.data.model.ApiConfig];
 * [ApiTransactionSource] builds the Retrofit instance and calls this.
 */
interface SePayApi {

    /**
     * @param bearer full `Authorization` header value, e.g. `"Bearer <token>"`.
     * @param account optional account-number filter (null = all accounts on the token).
     * @param limit max rows to return.
     */
    @GET("userapi/transactions/list")
    suspend fun list(
        @Header("Authorization") bearer: String,
        @Query("account_number") account: String?,
        @Query("limit") limit: Int,
    ): SePayListResponse
}
