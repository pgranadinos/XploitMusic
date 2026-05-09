package com.auramusic.app.core.di

import com.auramusic.app.data.provider.config.DefaultProviderConfigManager
import com.auramusic.app.data.provider.config.LogcatProviderMetricsLogger
import com.auramusic.app.data.provider.impl.ProviderGatewayImpl
import com.auramusic.app.domain.provider.ProviderGateway
import com.auramusic.app.domain.provider.config.ProviderConfigManager
import com.auramusic.app.domain.provider.config.ProviderMetricsLogger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProviderModule {

    @Binds
    @Singleton
    abstract fun bindProviderGateway(impl: ProviderGatewayImpl): ProviderGateway

    @Binds
    @Singleton
    abstract fun bindProviderConfigManager(impl: DefaultProviderConfigManager): ProviderConfigManager

    @Binds
    @Singleton
    abstract fun bindProviderMetricsLogger(impl: LogcatProviderMetricsLogger): ProviderMetricsLogger
}
