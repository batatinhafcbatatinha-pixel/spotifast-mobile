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

    // ----- Sorting and filtering, remembered between launches -----

    private val prefs = app.getSharedPreferences("view", android.content.Context.MODE_PRIVATE)

    var librarySort by mutableStateOf(
        enumPref("library_sort", LibrarySort.Recent),
    )
        private set
    var libraryShow by mutableStateOf(LibraryShow.All)
    var libraryText by mutableStateOf("")
    var songSort by mutableStateOf(enumPref("song_sort", SongSort.Default))
        private set
    var songFilter by mutableStateOf(SongFilter())

    /** Last play time by playlist or album URI, from the recent history. */
    private var played by mutableStateOf<Map<String, String>>(emptyMap())

    private inline fun <reified T : Enum<T>> enumPref(key: String, default: T): T =
        prefs.getString(key, null)?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    var appearance by mutableStateOf(enumPref("appearance", io.github.batatinha.spotifast.ui.Appearance.System))
        private set
    var accent by mutableStateOf(enumPref("accent", io.github.batatinha.spotifast.ui.Accent.Green))
        private set

    /** Colours from the cover playing, for the dynamic theme. */
    var cover by mutableStateOf<io.github.batatinha.spotifast.ui.CoverColors?>(null)
        private set
    private var coverUrl: String? = null

    private fun followCover() {
        if (appearance != io.github.batatinha.spotifast.ui.Appearance.Dynamic) return
        val url = player.track?.artUrl ?: return
        if (url == coverUrl) return
        coverUrl = url
        viewModelScope.launch {
            coverColors(getApplication(), url)?.let { if (coverUrl == url) cover = it }
        }
    }

    fun setTheme(appearance: io.github.batatinha.spotifast.ui.Appearance, accent: io.github.batatinha.spotifast.ui.Accent) {
        coverUrl = null
        this.appearance = appearance
        this.accent = accent
        prefs.edit().putString("appearance", appearance.name).putString("accent", accent.name).apply()
        followCover()
    }

    // ----- Themes people make (THEMES.md) -----

    var customThemes by mutableStateOf(io.github.batatinha.spotifast.ui.CustomTheme.listFrom(prefs.getString("custom_themes", null)))
        private set

    /** The imported theme in use, when [appearance] is Custom. */
    val customTheme: io.github.batatinha.spotifast.ui.CustomTheme?
        get() = if (appearance == io.github.batatinha.spotifast.ui.Appearance.Custom) {
            customThemes.firstOrNull { it.name == customName }
        } else {
            null
        }
    private var customName by mutableStateOf(prefs.getString("custom_theme", null))

    /**
     * Adds a theme from its JSON and switches to it. One with the same name
     * replaces the old one, so editing a theme and importing it again works.
     * Returns why it could not be read, or null.
     */
    fun importTheme(text: String): String? {
        val theme = try {
            io.github.batatinha.spotifast.ui.CustomTheme.parse(text)
        } catch (e: IllegalArgumentException) {
            return e.message
        }
        customThemes = customThemes.filterNot { it.name == theme.name } + theme
        prefs.edit().putString("custom_themes", io.github.batatinha.spotifast.ui.CustomTheme.store(customThemes)).apply()
        useCustomTheme(theme)
        return null
    }

    fun useCustomTheme(theme: io.github.batatinha.spotifast.ui.CustomTheme) {
        customName = theme.name
        prefs.edit().putString("custom_theme", theme.name).apply()
        setTheme(io.github.batatinha.spotifast.ui.Appearance.Custom, accent)
    }

    fun removeTheme(theme: io.github.batatinha.spotifast.ui.CustomTheme) {
        customThemes = customThemes - theme
        prefs.edit().putString("custom_themes", io.github.batatinha.spotifast.ui.CustomTheme.store(customThemes)).apply()
        if (customTheme == null && appearance == io.github.batatinha.spotifast.ui.Appearance.Custom) {
            setTheme(io.github.batatinha.spotifast.ui.Appearance.System, accent)
        }
    }

    fun sortLibrary(sort: LibrarySort) {
        librarySort = sort
        prefs.edit().putString("library_sort", sort.name).apply()
        if (sort == LibrarySort.Played) loadPlayed()
    }

    fun sortSongs(sort: SongSort) {
        songSort = sort
        prefs.edit().putString("song_sort", sort.name).apply()
    }

    val library: List<Collection>
        get() = arrangeLibrary(playlists + albums, librarySort, libraryShow, libraryText, userId, played)

    fun shownSongs(collection: Collection): List<Song>? =
        songs[collection.uri]?.let { arrangeSongs(it, songSort, songFilter) }

    private fun loadPlayed() = viewModelScope.launch {
        played = runCatching { Spotify.recentlyPlayedContexts() }.getOrDefault(played)
    }

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
                    if (json != null) {
                        player = PlayerState.parse(json)
                        followCover()
                    }
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
            loadPlayed()
        } catch (e: Exception) {
            failed(e)
        }
        libraryLoading = false
    }

    /** Songs still arriving for a collection, by URI: how many of how many. */
    val songsLoading = mutableStateMapOf<String, Pair<Int, Int>>()

    fun open(collection: Collection) {
        songFilter = SongFilter()
        stack.add(collection)
        if (collection.uri in songs) return
        viewModelScope.launch {
            try {
                Spotify.streamSongs(collection) { read, total ->
                    songs[collection.uri] = read
                    if (read.size < total) {
                        songsLoading[collection.uri] = read.size to total
                    } else {
                        songsLoading.remove(collection.uri)
                    }
                }
            } catch (e: Exception) {
                if (collection.uri !in songs) songs[collection.uri] = emptyList()
                failed(e)
            } finally {
                songsLoading.remove(collection.uri)
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

    /**
     * Plays [shown] from [index]. In Spotify's own order the collection
     * itself is played, so Spotify keeps its context; sorted or filtered,
     * the songs go as a list in the order on screen.
     */
    fun playShown(collection: Collection, shown: List<Song>, index: Int?, shuffle: Boolean = false) {
        // An artist's context ignores the song chosen, so its top songs go as a list.
        if (songSort == SongSort.Default && !songFilter.active && collection.kind != Collection.Kind.Artist) {
            play(collection, index?.let { shown.getOrNull(it) }, shuffle)
            return
        }
        if (!status.playbackSignedIn) {
            error = "Set up playback on this phone first."
            return
        }
        val playable = shown.filter { it.playable }
        if (playable.isEmpty()) return
        val start = index?.let { playable.indexOf(shown[it]) }?.coerceAtLeast(0) ?: 0
        val uris = JSONArray()
        playable.forEach { uris.put(it.uri) }
        val command = JSONObject().put("cmd", "load").put("uris", uris).put("shuffle", shuffle)
        if (!shuffle) command.put("index", start)
        send(command)
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
