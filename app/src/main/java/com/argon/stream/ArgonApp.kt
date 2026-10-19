package com.argon.stream

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.argon.stream.core.diagnostics.CrashLog
import com.argon.stream.ui.components.isTelevision
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class ArgonApp : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        // Cross-fading every poster costs GPU time that TV boxes don't have; keep it for phones.
        return ImageLoader.Builder(context)
            .crossfade(!context.isTelevision())
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }
            .build()
    }
}