package com.loaloaloa.di

import com.loaloaloa.BuildConfig
import com.loaloaloa.source.api.ApiTransactionSource
import com.loaloaloa.source.api.SePayConnectionTester
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Provides the HTTP plumbing for the SePay polling source. The Retrofit instance
 * itself is built per-poll inside [com.loaloaloa.source.api.ApiTransactionSource]
 * (it depends on the user-supplied base URL), so only the shared [OkHttpClient] is
 * provided here. [com.loaloaloa.source.api.SePayNormalizer] and the source
 * are `@Inject constructor`, so they need no explicit provider.
 *
 * The source is intentionally NOT bound to the [com.loaloaloa.source.TransactionSource]
 * interface: the notification path does not flow through that seam, and the API
 * collector resolves the concrete type directly in the listener service.
 */
@Module
@InstallIn(SingletonComponent::class)
object ApiModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    /** Expose the polling source's one-shot connectivity check to the Settings UI. */
    @Provides
    @Singleton
    fun provideSePayConnectionTester(source: ApiTransactionSource): SePayConnectionTester = source
}
