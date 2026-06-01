package com.tingting.notifier.di

import com.tingting.notifier.data.repository.TransactionRepository
import com.tingting.notifier.data.repository.UserSettingsRepository
import com.tingting.notifier.domain.TransactionRepositoryImpl
import com.tingting.notifier.domain.UserSettingsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindUserSettingsRepository(impl: UserSettingsRepositoryImpl): UserSettingsRepository
}
