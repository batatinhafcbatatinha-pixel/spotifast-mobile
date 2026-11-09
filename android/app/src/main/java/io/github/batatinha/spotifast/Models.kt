package io.github.batatinha.spotifast

import org.json.JSONArray
import org.json.JSONObject

data class Status(
    val webSignedIn: Boolean = false,
    val clientId: String? = null,
    val playbackSignedIn: Boolean = false,
    val engineConnected: Boolean = false,
) {
    companion object {
        fun parse(json: String): Status {
            val o = JSONObject(json)
            return Status(
                webSignedIn = o.optBoolean("web_signed_in"),
                clientId = o.optStringOrNull("client_id"),
                playbackSignedIn = o.optBoolean("playback_signed_in"),
                engineConnected = o.optBoolean("engine_connected"),
            )
        }
    }
}

/** Anything that opens a list of songs: a playlist, an album, Liked Songs. */
data class Collection(
    val id: String,
    val uri: String,
    val name: String,
    val subtitle: String,
    val imageUrl: String?,
    val kind: Kind,
    /** Position in Spotify's own order (the library's, or the results'). */
    val order: Int = 0,
    val ownerId: String? = null,
    val total: Int = 0,
    /** ISO time an album was saved, when Spotify says. */
    val addedAt: String? = null,
    val releaseDate: String? = null,
) {
    enum class Kind { Playlist, Album, Liked, Artist }
}

data class Song(
    val uri: String,
    val name: String,
    val artists: String,
    val album: String,
    val imageUrl: String?,
    val durationMs: Long,
    val playable: Boolean,
    /** Position in the playlist or album as Spotify orders it. */
    val order: Int = 0,
    /** ISO time the song was added to the playlist or saved. */
    val addedAt: String? = null,
    val releaseDate: String? = null,
    val explicit: Boolean = false,
    /** 0 to 100, or -1 when Spotify leaves it out. */
    val popularity: Int = -1,
)

data class SearchResults(
    val songs: List<Song> = emptyList(),
    val albums: List<Collection> = emptyList(),
    val artists: List<Collection> = emptyList(),
    val playlists: List<Collection> = emptyList(),
)

data class NowPlaying(
    val uri: String,
    val title: String,
    val artists: String,
    val album: String,
    val artUrl: String?,
    val durationMs: Long,
)

data class PlayerState(
    val connected: Boolean = false,
    val playing: Boolean = false,
    val loading: Boolean = false,
    val positionMs: Long = 0,
    val positionAt: Long = 0,
    val track: NowPlaying? = null,
    val shuffle: Boolean = false,
    val repeat: String = "off",
    val activeClient: String = "",
    val error: String? = null,
) {
    /** The position now, carried forward from the last report while playing. */
    fun positionNow(): Long {
        val elapsed = if (playing) System.currentTimeMillis() - positionAt else 0
        val position = positionMs + elapsed.coerceAtLeast(0)
        return track?.let { position.coerceAtMost(it.durationMs) } ?: position
    }

    companion object {
        fun parse(json: String): PlayerState {
            val o = JSONObject(json)
            val t = o.optJSONObject("track")
            return PlayerState(
                connected = o.optBoolean("connected"),
                playing = o.optBoolean("playing"),
                loading = o.optBoolean("loading"),
                positionMs = o.optLong("position_ms"),
                positionAt = o.optLong("position_at"),
                track = t?.let {
                    NowPlaying(
                        uri = it.optString("uri"),
                        title = it.optString("title"),
                        artists = it.optJSONArray("artists").strings().joinToString(", "),
                        album = it.optString("album"),
                        artUrl = it.optStringOrNull("art_url"),
                        durationMs = it.optLong("duration_ms"),
                    )
                },
                shuffle = o.optBoolean("shuffle"),
                repeat = o.optString("repeat", "off"),
                activeClient = o.optString("active_client"),
                error = o.optStringOrNull("error"),
            )
        }
    }
}

// ----- Web API parsing -----

fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }

fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

/** The largest image that is still small enough for a list row or a cover. */
fun JSONArray?.imageUrl(): String? {
    val images = objects()
    if (images.isEmpty()) return null
    return images.firstOrNull { it.optInt("width", 0) in 200..700 }?.optString("url")
        ?: images.first().optString("url")
}

fun artistNames(o: JSONObject): String =
    o.optJSONArray("artists").objects().joinToString(", ") { it.optString("name") }

fun parseSong(o: JSONObject, albumImage: String? = null): Song? {
    val uri = o.optString("uri")
    if (uri.isEmpty()) return null
    val album = o.optJSONObject("album")
    return Song(
        uri = uri,
        name = o.optString("name"),
        artists = artistNames(o).ifEmpty { o.optJSONObject("show")?.optString("name") ?: "" },
        album = album?.optString("name") ?: "",
        imageUrl = album?.optJSONArray("images").imageUrl()
            ?: o.optJSONArray("images").imageUrl()
            ?: albumImage,
        durationMs = o.optLong("duration_ms"),
        playable = o.optBoolean("is_playable", true) && !o.optBoolean("is_local"),
        releaseDate = album?.optStringOrNull("release_date") ?: o.optStringOrNull("release_date"),
        explicit = o.optBoolean("explicit"),
        popularity = if (o.has("popularity") && !o.isNull("popularity")) o.optInt("popularity") else -1,
    )
}

fun parsePlaylist(o: JSONObject): Collection = Collection(
    id = o.optString("id"),
    uri = o.optString("uri"),
    name = o.optString("name"),
    subtitle = o.optJSONObject("owner")?.optString("display_name") ?: "",
    imageUrl = o.optJSONArray("images").imageUrl(),
    kind = Collection.Kind.Playlist,
    ownerId = o.optJSONObject("owner")?.optStringOrNull("id"),
    // The current API counts songs under `items`; older answers say `tracks`.
    total = (o.optJSONObject("items") ?: o.optJSONObject("tracks"))?.optInt("total") ?: 0,
)

fun parseAlbum(o: JSONObject): Collection = Collection(
    id = o.optString("id"),
    uri = o.optString("uri"),
    name = o.optString("name"),
    subtitle = artistNames(o),
    imageUrl = o.optJSONArray("images").imageUrl(),
    kind = Collection.Kind.Album,
    total = o.optInt("total_tracks"),
    releaseDate = o.optStringOrNull("release_date"),
)

fun parseArtist(o: JSONObject): Collection = Collection(
    id = o.optString("id"),
    uri = o.optString("uri"),
    name = o.optString("name"),
    subtitle = "Artist",
    imageUrl = o.optJSONArray("images").imageUrl(),
    kind = Collection.Kind.Artist,
)
