package io.github.batatinha.spotifast

import android.app.Application

class SpotifastApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Native.init(this, filesDir.absolutePath, cacheDir.absolutePath)
    }
}
