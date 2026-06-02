package com.loaloaloa.di

import com.loaloaloa.relay.OkHttpRelayDirectory
import com.loaloaloa.relay.RelayDirectory
import com.loaloaloa.relay.RelayRegistrar
import com.loaloaloa.relay.WorkManagerRelayRegistrar
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RelayModule {

    @Binds
    @Singleton
    abstract fun bindRelayRegistrar(impl: WorkManagerRelayRegistrar): RelayRegistrar

    @Binds
    @Singleton
    abstract fun bindRelayDirectory(impl: OkHttpRelayDirectory): RelayDirectory
}
