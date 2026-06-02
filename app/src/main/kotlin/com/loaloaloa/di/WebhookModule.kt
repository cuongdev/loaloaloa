package com.loaloaloa.di

import com.loaloaloa.webhook.OkHttpWebhookTester
import com.loaloaloa.webhook.WebhookTester
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the outbound-webhook seams. [com.loaloaloa.webhook.WebhookSender] is
 * `@Inject constructor` + `@Singleton`, and the `OkHttpClient` is already provided by
 * [ApiModule], so only the [WebhookTester] interface needs a binding here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WebhookModule {

    @Binds
    @Singleton
    abstract fun bindWebhookTester(impl: OkHttpWebhookTester): WebhookTester
}
