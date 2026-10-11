package com.xploitmusic.app.core.di

import com.xploitmusic.app.data.provider.config.DefaultProviderConfigManager
import com.xploitmusic.app.data.provider.config.LogcatProviderMetricsLogger
import com.xploitmusic.app.data.provider.impl.ProviderGatewayImpl
import com.xploitmusic.app.domain.provider.ProviderGateway
import com.xploitmusic.app.domain.provider.config.ProviderConfigManager
import com.xploitmusic.app.domain.provider.config.ProviderMetricsLogger
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
