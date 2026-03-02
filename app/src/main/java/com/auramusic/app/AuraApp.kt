package com.auramusic.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AuraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize NewPipeExtractor
        org.schabi.newpipe.extractor.NewPipe.init(
            com.auramusic.app.data.remote.NewPipeDownloader(
                okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
            )
        )
    }
}
