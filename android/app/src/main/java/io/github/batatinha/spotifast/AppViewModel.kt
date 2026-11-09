package io.github.batatinha.spotifast

import android.app.Application
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class Tab { Library, Search }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    var status by mutableStateOf(Status())
        private set

    /** What a long operation is doing, shown with a spinner; null when idle. */
    var busy by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)

    var tab by mutableStateOf(Tab.Library)
    val stack = mutableStateListOf<Collection>()
    val songs = mutableStateMapOf<String, List<Song>>()
    var showNowPlaying by mutableStateOf(false)

    var playlists by mutableStateOf<List<Collection>>(emptyList())
        private set
    var albums by mutableStateOf<List<Collection>>(emptyList())
        private set
    var libraryLoading by mutableStateOf(false)
        private set

    var query by mutableStateOf("")
    var results by mutableStateOf(SearchResults())
        private set
    var searching by mutableStateOf(false)
        private set
    private var searchJob: Job? = null

    var player by mutableStateOf(PlayerState())
        private set

    /** Whether the interface is on screen; the media session keeps its own watch. */
    var visible = true
    private var userId: String? = null

    private val _openUrl = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openUrl = _openUrl.asSharedFlow()

    val liked: Collection
        get() = Collection(
            id = "liked",
            uri = "spotify:user:${userId ?: "me"}:collection",
            name = "Liked Songs",
            subtitle = "Your saved songs",
            imageUrl = null,
            kind = Collection.Kind.Liked,
        )

    private val deviceName = "Spotifast (${Build.MODEL})"

    init {
        viewModelScope.launch {
            refreshStatus()
            if (status.webSignedIn) loadLibrary()
            if (status.playbackSignedIn) connectQuietly()
        }
        viewModelScope.launch {
            while (true) {
                if (visible) {
                    val json = runCatching { io { Native.playerState() } }.getOrNull()
                    if (json != null) player = PlayerState.parse(json)
                }
                delay(500)
            }
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private suspend fun refreshStatus() {
        status = Status.parse(io { Native.status() })
    }

    private fun failed(e: Throwable) {
        val message = e.message ?: e.toString()
        // Cancelling is the user's own choice, not an error to report.
        if (message != "sign-in cancelled") error = message
    }

    // ----- Sign-in -----

    fun signIn(clientId: String) = login("Waiting for Spotify in the browser…") {
        io { Native.beginWebLogin(clientId.trim()) }
    }.also { it.invokeOnCompletion { viewModelScope.launch { if (status.webSignedIn) loadLibrary() } } }

    fun setUpPlayback() = login("Waiting for playback approval…") {
        io { Native.beginPlaybackLogin() }
    }.also {
        it.invokeOnCompletion {
            viewModelScope.launch {
                if (status.playbackSignedIn) {
                    busy = "Connecting playback…"
                    try {
                        io { Native.connect(deviceName) }
                    } catch (e: Exception) {
                        failed(e)
                    }
                    busy = null
                    refreshStatus()
                }
            }
        }
    }

    private fun login(message: String, begin: suspend () -> String): Job = viewModelScope.launch {
        error = null
        try {
            val url = begin()
            busy = message
            _openUrl.emit(url)
            io { Native.awaitLogin() }
        } catch (e: Exception) {
            failed(e)
        } finally {
            busy = null
            refreshStatus()
        }
    }

    fun cancelLogin() {
        Native.cancelLogin()
    }

    fun signOut() = viewModelScope.launch {
        io { Native.signOut() }
        playlists = emptyList()
        albums = emptyList()
        songs.clear()
        stack.clear()
        refreshStatus()
    }

    // ----- Library -----

    fun loadLibrary() = viewModelScope.launch {
        libraryLoading = true
        try {
            userId = Spotify.me().optString("id")
            playlists = Spotify.myPlaylists()
            albums = Spotify.savedAlbums()
        } catch (e: Exception) {
            failed(e)
        }
        libraryLoading = false
    }

    fun open(collection: Collection) {
        stack.add(collection)
        if (collection.uri in songs) return
        viewModelScope.launch {
            try {
                songs[collection.uri] = Spotify.songs(collection)
            } catch (e: Exception) {
                songs[collection.uri] = emptyList()
                failed(e)
            }
        }
    }

    fun back(): Boolean {
        if (showNowPlaying) {
            showNowPlaying = false
            return true
        }
        if (stack.isEmpty()) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun search(text: String) {
        query = text
        searchJob?.cancel()
        if (text.isBlank()) {
            results = SearchResults()
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            searching = true
            try {
                results = Spotify.search(text)
            } catch (e: Exception) {
                failed(e)
            }
            searching = false
        }
    }

    // ----- Playback -----

    private suspend fun connectQuietly() {
        try {
            io { Native.connect(deviceName) }
        } catch (e: Exception) {
            failed(e)
        }
        refreshStatus()
    }

    private suspend fun ensureConnected() {
        if (!player.connected) {
            busy = "Connecting playback…"
            try {
                io { Native.connect(deviceName) }
            } finally {
                busy = null
            }
            refreshStatus()
        }
    }

    private fun send(json: JSONObject) = viewModelScope.launch {
        try {
            ensureConnected()
            io { Native.command(json.toString()) }
            player = PlayerState.parse(io { Native.playerState() })
        } catch (e: Exception) {
            failed(e)
            // A refused playback grant is dropped; show the setup card again.
            refreshStatus()
        }
    }

    /** Plays [song] within [collection], or on its own when there is none. */
    fun play(collection: Collection?, song: Song?, shuffle: Boolean = false) {
        if (!status.playbackSignedIn) {
            error = "Set up playback on this phone first."
            return
        }
        val command = JSONObject().put("cmd", "load").put("shuffle", shuffle)
        if (collection != null) {
            command.put("context", collection.uri)
            song?.let { command.put("offset", it.uri) }
        } else if (song != null) {
            command.put("uris", JSONArray().put(song.uri))
        }
        send(command)
    }

    fun toggle() = send(JSONObject().put("cmd", "toggle"))
    fun next() = send(JSONObject().put("cmd", "next"))
    fun previous() = send(JSONObject().put("cmd", "prev"))
    fun seek(ms: Long) = send(JSONObject().put("cmd", "seek").put("ms", ms))
    fun setShuffle(on: Boolean) = send(JSONObject().put("cmd", "shuffle").put("on", on))
    fun cycleRepeat() = send(
        JSONObject().put("cmd", "repeat").put(
            "mode",
            when (player.repeat) {
                "off" -> "context"
                "context" -> "track"
                else -> "off"
            },
        ),
    )
    fun queue(song: Song) = send(JSONObject().put("cmd", "queue").put("uri", song.uri))
    fun transfer() = send(JSONObject().put("cmd", "transfer"))
}
