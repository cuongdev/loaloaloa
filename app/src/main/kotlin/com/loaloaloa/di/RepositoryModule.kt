package com.loaloaloa.di

import com.loaloaloa.data.repository.TransactionRepository
import com.loaloaloa.data.repository.UserSettingsRepository
import com.loaloaloa.domain.TransactionRepositoryImpl
import com.loaloaloa.domain.UserSettingsRepositoryImpl
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
