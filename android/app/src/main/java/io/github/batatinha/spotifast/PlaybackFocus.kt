package io.github.batatinha.spotifast

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * The app's one audio focus request. Two requests from the same process
 * take focus from each other, and the loser would pause the music it is
 * itself playing; a single request, made again while held, changes nothing.
 */
object PlaybackFocus {
    private var audioManager: AudioManager? = null
    private var request: AudioFocusRequest? = null
    private var held = false
    private var pausedForFocus = false

    /** What to send to the core: "play" or "pause". */
    var onCommand: (String) -> Unit = {}

    @Synchronized
    private fun request(context: Context): AudioFocusRequest {
        request?.let { return it }
        audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
        return AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setOnAudioFocusChangeListener(::changed)
            .build()
            .also { request = it }
    }

    private fun changed(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                held = false
                pausedForFocus = false
                onCommand("pause")
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                pausedForFocus = true
                onCommand("pause")
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                held = true
                if (pausedForFocus) {
                    pausedForFocus = false
                    onCommand("play")
                }
            }
        }
    }

    /** Called while the music plays; asks for focus once. */
    @Synchronized
    fun playing(context: Context) {
        if (held) return
        val request = request(context)
        held = audioManager?.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    @Synchronized
    fun release() {
        val request = request ?: return
        audioManager?.abandonAudioFocusRequest(request)
        held = false
        pausedForFocus = false
    }
}
