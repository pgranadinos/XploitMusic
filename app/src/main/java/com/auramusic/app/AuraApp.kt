package com.auramusic.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkerFactory
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.auramusic.app.core.util.CrashHandler
import com.auramusic.app.data.repository.OfflineManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AuraApp : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var offlineManager: OfflineManager

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = createWorkManagerConfiguration(workerFactory)

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        
        // Schedule periodic storage integrity check + LRU eviction
        offlineManager.schedulePeriodicCleanup()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }
}

internal fun createWorkManagerConfiguration(workerFactory: WorkerFactory): Configuration =
    Configuration.Builder()
        .setWorkerFactory(workerFactory)
        .build()
