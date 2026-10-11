package com.xploitmusic.app.data.provider.config

import com.xploitmusic.app.BuildConfig
import com.xploitmusic.app.domain.provider.OperationType
import com.xploitmusic.app.domain.provider.config.ProviderConfigManager
import javax.inject.Inject

class DefaultProviderConfigManager @Inject constructor() : ProviderConfigManager {
    override fun getFallbackOrder(operation: OperationType): List<String> {
        val optionalBff = if (BuildConfig.ALLOW_LOCALHOST) listOf("XPLOIT_BFF") else emptyList()
        return when(operation) {
            // YouTube Music owns catalogue quality; NewPipe only resolves playback URLs.
            OperationType.GET_STREAM -> listOf("LOCAL_YOUTUBE") + optionalBff + listOf("PIPED", "INVIDIOUS")
            OperationType.SEARCH -> listOf("YOUTUBE_MUSIC", "LOCAL_YOUTUBE", "PIPED", "INVIDIOUS") + optionalBff
            OperationType.GET_RELATED -> listOf("YOUTUBE_MUSIC", "LOCAL_YOUTUBE", "PIPED", "INVIDIOUS") + optionalBff
            OperationType.GET_TRENDING -> listOf("YOUTUBE_MUSIC", "PIPED") + optionalBff
            OperationType.GET_TRACK -> listOf("YOUTUBE_MUSIC", "LOCAL_YOUTUBE", "PIPED", "INVIDIOUS") + optionalBff
        }
    }
}
