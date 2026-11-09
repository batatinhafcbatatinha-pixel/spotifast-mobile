package io.github.batatinha.spotifast

/**
 * Sorting and filtering the library and song lists. All of it happens on
 * the phone over what Spotify already sent: the Web API takes no sort
 * parameters for playlists, saved albums or playlist items.
 */

enum class LibrarySort(val label: String) {
    /** Spotify's library order: what you added, followed or made last first. */
    Recent("Recently added"),
    Played("Recently played"),
    Name("Name (A–Z)"),
    NameDesc("Name (Z–A)"),
    Creator("Creator"),
    MostSongs("Most songs"),
    FewestSongs("Fewest songs"),
}

enum class LibraryShow(val label: String) { All("All"), Playlists("Playlists"), Albums("Albums"), Mine("By you") }

enum class SongSort(val label: String) {
    Default("Default order"),
    AddedNew("Recently added"),
    AddedOld("Oldest added"),
    Title("Title (A–Z)"),
    Artist("Artist (A–Z)"),
    Album("Album (A–Z)"),
    Shortest("Shortest first"),
    Longest("Longest first"),
    Newest("Newest releases"),
    Oldest("Oldest releases"),
    Popular("Most popular"),
}

enum class LengthFilter(val label: String, val range: LongRange) {
    Any("Any length", 0L..kotlin.Long.MAX_VALUE),
    Brief("Under 3 min", 0L until 180_000L),
    Medium("3–5 min", 180_000L..300_000L),
    Lengthy("Over 5 min", 300_001L..kotlin.Long.MAX_VALUE),
}

data class SongFilter(
    val text: String = "",
    val length: LengthFilter = LengthFilter.Any,
    val hideExplicit: Boolean = false,
) {
    val active: Boolean get() = text.isNotBlank() || length != LengthFilter.Any || hideExplicit
}

private fun key(text: String) = text.trim().lowercase()

private fun Collection.matches(text: String): Boolean =
    text.isBlank() || key(name).contains(key(text)) || key(subtitle).contains(key(text))

fun arrangeLibrary(
    items: List<Collection>,
    sort: LibrarySort,
    show: LibraryShow,
    text: String,
    myId: String?,
    played: Map<String, String>,
): List<Collection> {
    val shown = items.filter {
        when (show) {
            LibraryShow.All -> true
            LibraryShow.Playlists -> it.kind == Collection.Kind.Playlist
            LibraryShow.Albums -> it.kind == Collection.Kind.Album
            LibraryShow.Mine -> it.kind == Collection.Kind.Playlist && it.ownerId != null && it.ownerId == myId
        } && it.matches(text)
    }
    return when (sort) {
        // Playlists first in library order, then albums by when they were saved.
        LibrarySort.Recent -> shown.sortedWith(
            compareBy<Collection> { it.kind != Collection.Kind.Playlist }
                .thenByDescending { it.addedAt ?: "" }
                .thenBy { it.order },
        )
        // Played ones by last play; the rest keep the library order after them.
        LibrarySort.Played -> shown.sortedWith(
            compareByDescending<Collection> { played[it.uri] ?: "" }
                .thenBy { it.kind != Collection.Kind.Playlist }
                .thenBy { it.order },
        )
        LibrarySort.Name -> shown.sortedBy { key(it.name) }
        LibrarySort.NameDesc -> shown.sortedByDescending { key(it.name) }
        LibrarySort.Creator -> shown.sortedWith(compareBy({ key(it.subtitle) }, { key(it.name) }))
        LibrarySort.MostSongs -> shown.sortedByDescending { it.total }
        LibrarySort.FewestSongs -> shown.sortedBy { it.total }
    }
}

fun arrangeSongs(songs: List<Song>, sort: SongSort, filter: SongFilter): List<Song> {
    val shown = songs.filter { song ->
        (filter.text.isBlank() ||
            key(song.name).contains(key(filter.text)) ||
            key(song.artists).contains(key(filter.text)) ||
            key(song.album).contains(key(filter.text))) &&
            song.durationMs in filter.length.range &&
            !(filter.hideExplicit && song.explicit)
    }
    return when (sort) {
        SongSort.Default -> shown.sortedBy { it.order }
        SongSort.AddedNew -> shown.sortedByDescending { it.addedAt ?: "" }
        SongSort.AddedOld -> shown.sortedBy { it.addedAt ?: "￿" }
        SongSort.Title -> shown.sortedBy { key(it.name) }
        SongSort.Artist -> shown.sortedWith(compareBy({ key(it.artists) }, { key(it.album) }, { it.order }))
        SongSort.Album -> shown.sortedWith(compareBy({ key(it.album) }, { it.order }))
        SongSort.Shortest -> shown.sortedBy { it.durationMs }
        SongSort.Longest -> shown.sortedByDescending { it.durationMs }
        SongSort.Newest -> shown.sortedByDescending { it.releaseDate ?: "" }
        SongSort.Oldest -> shown.sortedBy { it.releaseDate ?: "￿" }
        SongSort.Popular -> shown.sortedByDescending { it.popularity }
    }
}

/** Sorts that only make sense when Spotify sent the data behind them. */
fun songSorts(songs: List<Song>): List<SongSort> = SongSort.entries.filter { sort ->
    when (sort) {
        SongSort.AddedNew, SongSort.AddedOld -> songs.any { it.addedAt != null }
        SongSort.Newest, SongSort.Oldest -> songs.any { it.releaseDate != null }
        SongSort.Popular -> songs.any { it.popularity > 0 }
        else -> true
    }
}
