package io.github.batatinha.spotifast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors

/**
 * A Media3 player whose audio is librespot's, in the Rust core. It mirrors
 * the core's state so the media notification, the lock screen, Bluetooth
 * and Android Auto controls work, and sends their buttons back as commands.
 * It holds audio focus through [PlaybackFocus] while playing and pauses
 * when headphones go.
 */
@UnstableApi
class NativePlayer(private val context: Context) : SimpleBasePlayer(Looper.getMainLooper()) {
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var current = PlayerState()
    private var lastJson = ""

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            send("""{"cmd":"pause"}""")
        }
    }
    private var noisyRegistered = false

    private val poll = object : Runnable {
        override fun run() {
            io.execute {
                val json = runCatching { Native.playerState() }.getOrNull()
                if (json != null && json != lastJson) {
                    handler.post {
                        lastJson = json
                        current = PlayerState.parse(json)
                        followPlayback()
                        invalidateState()
                    }
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    init {
        PlaybackFocus.onCommand = { cmd -> send("""{"cmd":"$cmd"}""") }
        handler.post(poll)
    }

    private fun send(json: String) {
        io.execute { runCatching { Native.command(json) } }
    }

    private fun followPlayback() {
        if (current.playing) {
            PlaybackFocus.playing(context)
            if (!noisyRegistered) {
                ContextCompat.registerReceiver(
                    context,
                    noisy,
                    IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                noisyRegistered = true
            }
        } else if (noisyRegistered) {
            context.unregisterReceiver(noisy)
            noisyRegistered = false
        }
    }

    override fun getState(): State {
        val track = current.track
        val commands = Player.Commands.Builder()
            .addAll(
                COMMAND_PLAY_PAUSE,
                COMMAND_STOP,
                COMMAND_SEEK_TO_NEXT,
                COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                COMMAND_SEEK_TO_PREVIOUS,
                COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                COMMAND_SET_SHUFFLE_MODE,
                COMMAND_SET_REPEAT_MODE,
                COMMAND_GET_CURRENT_MEDIA_ITEM,
                COMMAND_GET_TIMELINE,
                COMMAND_GET_METADATA,
            )
            .build()
        val builder = State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(current.playing, PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .setShuffleModeEnabled(current.shuffle)
            .setRepeatMode(
                when (current.repeat) {
                    "track" -> REPEAT_MODE_ONE
                    "context" -> REPEAT_MODE_ALL
                    else -> REPEAT_MODE_OFF
                },
            )
        if (track == null) {
            return builder.setPlaybackState(STATE_IDLE).build()
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artists)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.artUrl?.let(Uri::parse))
            .build()
        val item = MediaItemData.Builder(track.uri)
            .setMediaItem(MediaItem.Builder().setMediaId(track.uri).setMediaMetadata(metadata).build())
            .setMediaMetadata(metadata)
            .setDurationUs(track.durationMs * 1000)
            .setIsSeekable(true)
            .build()
        return builder
            .setPlaybackState(if (current.loading) STATE_BUFFERING else STATE_READY)
            .setPlaylist(listOf(item))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(
                PositionSupplier.getExtrapolating(current.positionNow(), if (current.playing) 1f else 0f),
            )
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        send(if (playWhenReady) """{"cmd":"play"}""" else """{"cmd":"pause"}""")
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        send("""{"cmd":"pause"}""")
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> send("""{"cmd":"next"}""")
            COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> send("""{"cmd":"prev"}""")
            else -> send("""{"cmd":"seek","ms":${positionMs.coerceAtLeast(0)}}""")
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        send("""{"cmd":"shuffle","on":$shuffleModeEnabled}""")
        return Futures.immediateVoidFuture()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        val mode = when (repeatMode) {
            REPEAT_MODE_ONE -> "track"
            REPEAT_MODE_ALL -> "context"
            else -> "off"
        }
        send("""{"cmd":"repeat","mode":"$mode"}""")
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        handler.removeCallbacks(poll)
        if (noisyRegistered) context.unregisterReceiver(noisy)
        PlaybackFocus.release()
        io.shutdown()
        return Futures.immediateVoidFuture()
    }
}
