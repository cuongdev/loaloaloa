package com.tingting.notifier.di

import com.tingting.notifier.ingest.TransactionIngestor
import com.tingting.notifier.parser.TransactionParser
import com.tingting.notifier.source.notification.DedupeGate
import com.tingting.notifier.source.notification.NotificationProcessor
import com.tingting.notifier.ui.debug.DebugIngest
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Qualifier for the IO dispatcher used by detection background work. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object SourceModule {

    @Provides
    @Singleton
    fun provideTransactionParser(): TransactionParser = TransactionParser()

    @Provides
    @Singleton
    fun provideNotificationProcessor(parser: TransactionParser): NotificationProcessor =
        NotificationProcessor(parser)

    @Provides
    @Singleton
    fun provideDedupeGate(): DedupeGate = DedupeGate()

    /** Seam for the Debug screen: run a sample through the real ingest funnel. */
    @Provides
    @Singleton
    fun provideDebugIngest(ingestor: TransactionIngestor): DebugIngest =
        DebugIngest { model -> ingestor.ingest(model) }

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
