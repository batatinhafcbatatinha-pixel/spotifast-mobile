package io.github.batatinha.spotifast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/** Where each page after the first starts, for [total] entries read [limit] at a time. */
fun remainingOffsets(entriesRead: Int, total: Int, limit: Int): List<Int> =
    if (entriesRead <= 0) emptyList() else (entriesRead until total step limit).toList()

/** The Web API calls the interface makes, through the Rust core. */
object Spotify {
    private suspend fun get(path: String): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(Native.api("GET", path, null))
    }

    /** Follows `next` links until [max] items have been read. */
    private suspend fun pages(first: String, max: Int = 10_000): List<JSONObject> {
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

    /** Entries that carry the song with the time it was added, numbered from [start]. */
    private fun saved(entries: List<JSONObject>, start: Int = 0): List<Song> = entries.mapNotNull { entry ->
        // The current API names the song `item`; older answers say `track`.
        (entry.optJSONObject("item") ?: entry.optJSONObject("track"))
            ?.let { parseSong(it) }
            ?.copy(addedAt = entry.optStringOrNull("added_at"))
    }.mapIndexed { i, song -> song.copy(order = start + i) }

    /**
     * The songs of [collection], page by page: [onPage] gets every song read
     * so far and the total Spotify reports, so a long playlist shows its
     * first songs at once and fills in while it is browsed.
     */
    suspend fun streamSongs(collection: Collection, onPage: (songs: List<Song>, total: Int) -> Unit) {
        val first = when (collection.kind) {
            Collection.Kind.Playlist -> "/playlists/${collection.id}/items?limit=100&additional_types=track,episode"
            Collection.Kind.Liked -> "/me/tracks?limit=50"
            else -> {
                val all = songs(collection)
                onPage(all, all.size)
                return
            }
        }
        val read = mutableListOf<Song>()
        val page = get(first)
        read += saved(page.optJSONArray("items").objects())
        val total = page.optInt("total", read.size)
        onPage(read.toList(), total)
        if (page.optStringOrNull("next") == null) return
        // The first page says how many there are, so the rest is asked for
        // a few pages at a time instead of one after another: 601 liked
        // songs took 13 round trips in a row (5.1 s on the emulator).
        val limit = page.optInt("limit", 50).coerceAtLeast(1)
        val offsets = remainingOffsets(entriesRead = page.optJSONArray("items")?.length() ?: 0, total = total, limit = limit)
        for (batch in offsets.chunked(PARALLEL_PAGES)) {
            val pages = coroutineScope {
                batch.map { offset -> async { get("$first&offset=$offset") } }.awaitAll()
            }
            pages.forEach { read += saved(it.optJSONArray("items").objects(), start = read.size) }
            onPage(read.toList(), total)
        }
    }

    /** Pages fetched at once; few enough to stay well inside Spotify's rate limit. */
    private const val PARALLEL_PAGES = 4

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
