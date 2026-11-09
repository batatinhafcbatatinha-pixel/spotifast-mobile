package io.github.batatinha.spotifast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/** The Web API calls the interface makes, through the Rust core. */
object Spotify {
    private suspend fun get(path: String): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(Native.api("GET", path, null))
    }

    /** Follows `next` links until [max] items have been read. */
    private suspend fun pages(first: String, max: Int = 2000): List<JSONObject> {
        val items = mutableListOf<JSONObject>()
        var next: String? = first
        while (next != null && items.size < max) {
            val page = get(next)
            items += page.optJSONArray("items").objects()
            next = page.optStringOrNull("next")
        }
        return items
    }

    suspend fun me(): JSONObject = get("/me")

    suspend fun myPlaylists(): List<Collection> =
        // Spotify can list one playlist twice across pages; show it once.
        pages("/me/playlists?limit=50").map(::parsePlaylist).distinctBy { it.uri }
            .mapIndexed { i, playlist -> playlist.copy(order = i) }

    suspend fun savedAlbums(): List<Collection> =
        pages("/me/albums?limit=50", max = 500).mapIndexedNotNull { i, saved ->
            saved.optJSONObject("album")?.let(::parseAlbum)
                ?.copy(order = i, addedAt = saved.optStringOrNull("added_at"))
        }.distinctBy { it.uri }

    suspend fun likedSongs(): List<Song> = saved(pages("/me/tracks?limit=50"))

    suspend fun playlistSongs(id: String): List<Song> =
        saved(pages("/playlists/$id/items?limit=50&additional_types=track,episode"))

    /** Entries that carry the song with the time it was added. */
    private fun saved(entries: List<JSONObject>): List<Song> = entries.mapNotNull { entry ->
        // The current API names the song `item`; older answers say `track`.
        (entry.optJSONObject("item") ?: entry.optJSONObject("track"))
            ?.let { parseSong(it) }
            ?.copy(addedAt = entry.optStringOrNull("added_at"))
    }.mapIndexed { i, song -> song.copy(order = i) }

    /**
     * When each playlist, album or artist was last played from, by URI, out
     * of the last 50 plays: all the history Spotify keeps for apps.
     */
    suspend fun recentlyPlayedContexts(): Map<String, String> {
        val played = mutableMapOf<String, String>()
        get("/me/player/recently-played?limit=50").optJSONArray("items").objects().forEach { play ->
            val uri = play.optJSONObject("context")?.optStringOrNull("uri") ?: return@forEach
            val at = play.optString("played_at")
            if (at > (played[uri] ?: "")) played[uri] = at
        }
        return played
    }

    suspend fun albumSongs(id: String): List<Song> {
        val album = get("/albums/$id")
        val cover = album.optJSONArray("images").imageUrl()
        val first = album.optJSONObject("tracks")
        val songs = first?.optJSONArray("items").objects().toMutableList()
        first?.optStringOrNull("next")?.let { songs += pages(it) }
        return songs.mapNotNull {
            parseSong(it, cover)?.copy(album = album.optString("name"), releaseDate = album.optStringOrNull("release_date"))
        }.mapIndexed { i, song -> song.copy(order = i) }
    }

    suspend fun artistTopSongs(id: String): List<Song> =
        get("/artists/$id/top-tracks").optJSONArray("tracks").objects().mapNotNull { parseSong(it) }
            .mapIndexed { i, song -> song.copy(order = i) }

    suspend fun search(query: String): SearchResults {
        val q = URLEncoder.encode(query, "UTF-8")
        val o = get("/search?q=$q&type=track,album,artist,playlist&limit=10")
        return SearchResults(
            songs = o.optJSONObject("tracks")?.optJSONArray("items").objects().mapNotNull { parseSong(it) },
            albums = o.optJSONObject("albums")?.optJSONArray("items").objects().map(::parseAlbum),
            artists = o.optJSONObject("artists")?.optJSONArray("items").objects().map(::parseArtist),
            playlists = o.optJSONObject("playlists")?.optJSONArray("items").objects().map(::parsePlaylist),
        )
    }

    suspend fun songs(collection: Collection): List<Song> = when (collection.kind) {
        Collection.Kind.Playlist -> playlistSongs(collection.id)
        Collection.Kind.Album -> albumSongs(collection.id)
        Collection.Kind.Liked -> likedSongs()
        Collection.Kind.Artist -> artistTopSongs(collection.id)
    }
}
