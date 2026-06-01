package com.tingting.notifier.di

import com.tingting.notifier.webhook.OkHttpWebhookTester
import com.tingting.notifier.webhook.WebhookTester
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the outbound-webhook seams. [com.tingting.notifier.webhook.WebhookSender] is
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
