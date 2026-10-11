package com.auramusic.app.data.provider.impl

import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.provider.MultimediaProvider
import com.auramusic.app.domain.provider.OperationType
import com.auramusic.app.domain.provider.ProviderError
import com.auramusic.app.domain.provider.ProviderGateway
import com.auramusic.app.domain.provider.config.ProviderConfigManager
import com.auramusic.app.domain.provider.config.ProviderMetricsLogger
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class ProviderGatewayImpl @Inject constructor(
    youTubeMusicProvider: YouTubeMusicProvider,
    localYouTubeProvider: LocalYouTubeExtractorProvider,
    bffProvider: BffProvider,
    pipedProvider: PipedProvider,
    invidiousProvider: InvidiousProvider,
    private val configManager: ProviderConfigManager,
    private val metricsLogger: ProviderMetricsLogger
) : ProviderGateway {

    private val providers: Map<String, MultimediaProvider> = mapOf(
        youTubeMusicProvider.providerId to youTubeMusicProvider,
        localYouTubeProvider.providerId to localYouTubeProvider,
        bffProvider.providerId to bffProvider,
        pipedProvider.providerId to pipedProvider,
        invidiousProvider.providerId to invidiousProvider
    )

    private suspend fun <T> executeWithFallback(
        operation: OperationType,
        isUsable: (T) -> Boolean = { true },
        action: suspend (MultimediaProvider) -> Result<T>
    ): Result<T> {
        val fallbackOrder = configManager.getFallbackOrder(operation)
        var lastError: ProviderError? = null

        for (providerId in fallbackOrder.distinct()) {
            val provider = providers[providerId] ?: continue
            val startTime = System.currentTimeMillis()
            
            val result = try {
                action(provider)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            val latency = System.currentTimeMillis() - startTime
            
            result.fold(
                onSuccess = {
                    if (isUsable(it)) {
                        metricsLogger.logSuccess(providerId, operation.name, latency)
                        return Result.success(it)
                    }

                    val emptyResultError = ProviderError.NotFound(providerId)
                    metricsLogger.logError(providerId, operation.name, emptyResultError)
                    lastError = emptyResultError
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    val providerError = error as? ProviderError
                        ?: ProviderError.NetworkIssue(providerId, error)
                    metricsLogger.logError(providerId, operation.name, providerError)
                    lastError = providerError
                    // Siempre continuamos al siguiente provider en la cadena de fallback.
                    // No interrumpimos en FatalError para garantizar máxima disponibilidad.
                }
            )
        }
        
        return Result.failure(lastError ?: ProviderError.FatalError("GATEWAY", "No suitable providers available for $operation"))
    }

    override suspend fun search(query: String): Result<List<Song>> {
        return executeWithFallback(OperationType.SEARCH, { it.isNotEmpty() }) { it.search(query) }
    }

    override suspend fun getTrack(videoId: String): Result<Song> {
        return executeWithFallback(OperationType.GET_TRACK) { it.getTrack(videoId) }
    }

    override suspend fun getStream(videoId: String): Result<String> {
        return executeWithFallback(OperationType.GET_STREAM, { it.isNotBlank() }) { it.getStream(videoId) }
    }

    override suspend fun getRelated(videoId: String): Result<List<Song>> {
        return executeWithFallback(OperationType.GET_RELATED, { it.isNotEmpty() }) { it.getRelated(videoId) }
    }

    override suspend fun getTrending(region: String): Result<List<Song>> {
        return executeWithFallback(OperationType.GET_TRENDING, { it.isNotEmpty() }) { it.getTrending(region) }
    }
}
