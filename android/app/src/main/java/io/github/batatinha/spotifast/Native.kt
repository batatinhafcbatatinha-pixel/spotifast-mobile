package io.github.batatinha.spotifast

import android.content.Context

/**
 * The Rust core (android/core). Calls that touch the network or wait for
 * the browser block: make them from [kotlinx.coroutines.Dispatchers.IO].
 * Failures throw [RuntimeException] with a message fit to show.
 */
object Native {
    init {
        System.loadLibrary("spotifast_android")
    }

    @JvmStatic external fun init(context: Context, dataDir: String, cacheDir: String)

    /** `{"web_signed_in","client_id","playback_signed_in","engine_connected"}` */
    @JvmStatic external fun status(): String

    /** Starts the Web API sign-in; returns the URL to open. */
    @JvmStatic external fun beginWebLogin(clientId: String): String

    /** Starts the playback sign-in; returns the URL to open. */
    @JvmStatic external fun beginPlaybackLogin(): String

    /** Blocks until the sign-in started last finishes. */
    @JvmStatic external fun awaitLogin()

    @JvmStatic external fun cancelLogin()

    @JvmStatic external fun signOut()

    /** One Web API request; [path] is under /v1 or a full `next` URL. */
    @JvmStatic external fun api(method: String, path: String, body: String?): String

    /** Makes this phone a Spotify Connect device. */
    @JvmStatic external fun connect(deviceName: String)

    @JvmStatic external fun disconnect()

    /** A player command as JSON, for example `{"cmd":"toggle"}`. */
    @JvmStatic external fun command(json: String): Boolean

    @JvmStatic external fun playerState(): String
}
