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
        pages("/me/playlists?limit=50").map(::parsePlaylist)

    suspend fun savedAlbums(): List<Collection> =
        pages("/me/albums?limit=50", max = 500).mapNotNull { it.optJSONObject("album")?.let(::parseAlbum) }

    suspend fun likedSongs(): List<Song> =
        pages("/me/tracks?limit=50").mapNotNull { it.optJSONObject("track")?.let { t -> parseSong(t) } }

    suspend fun playlistSongs(id: String): List<Song> =
        pages("/playlists/$id/items?limit=50&additional_types=track,episode").mapNotNull {
            // The current API names the entry `item`; older answers say `track`.
            (it.optJSONObject("item") ?: it.optJSONObject("track"))?.let { t -> parseSong(t) }
        }

    suspend fun albumSongs(id: String): List<Song> {
        val album = get("/albums/$id")
        val cover = album.optJSONArray("images").imageUrl()
        val first = album.optJSONObject("tracks")
        val songs = first?.optJSONArray("items").objects().toMutableList()
        first?.optStringOrNull("next")?.let { songs += pages(it) }
        return songs.mapNotNull { parseSong(it, cover)?.copy(album = album.optString("name")) }
    }

    suspend fun artistTopSongs(id: String): List<Song> =
        get("/artists/$id/top-tracks").optJSONArray("tracks").objects().mapNotNull { parseSong(it) }

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
