package com.xploitmusic.app

import android.app.Application
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.work.WorkerFactory
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.xploitmusic.app.core.util.CrashHandler
import com.xploitmusic.app.core.util.PlaybackLogManager
import com.xploitmusic.app.data.repository.OfflineManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class XploitApp : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var offlineManager: OfflineManager

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var playbackLogManager: PlaybackLogManager

    override val workManagerConfiguration: Configuration
        get() = createWorkManagerConfiguration(workerFactory)

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)

        // Log session header
        playbackLogManager.logSessionHeader(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            gitHash = BuildConfig.GIT_COMMIT_HASH,
            model = Build.MODEL,
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT
        )

        // Observe process lifecycle (foreground / background)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                playbackLogManager.log("LIFECYCLE", "INFO", "Aplicación pasó a primer plano (FOREGROUND)")
            }

            override fun onStop(owner: LifecycleOwner) {
                playbackLogManager.log("LIFECYCLE", "INFO", "Aplicación pasó a segundo plano (BACKGROUND)")
            }
        })

        playbackLogManager.startNetworkMonitoring()

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
