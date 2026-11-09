package io.github.batatinha.spotifast

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache

class SpotifastApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // Covers older versions saved to disk; nothing is written there now.
        cacheDir.resolve("image_cache").deleteRecursively()
        Native.init(this, filesDir.absolutePath, cacheDir.absolutePath)
    }

    /** Covers live in memory only, so the app's storage never grows. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .diskCache(null as DiskCache?)
        .respectCacheHeaders(false)
        .build()
}
