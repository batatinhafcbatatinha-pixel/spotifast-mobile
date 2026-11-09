import Foundation

/// The Web API calls the interface makes, through the Rust core.
enum Spotify {
    static func get(_ path: String) async throws -> JSON {
        let text = try await background { try Core.api("GET", path) }
        return parseJSON(text)
    }

    /// Follows `next` links until `max` items have been read.
    static func pages(_ first: String, max: Int = 10_000) async throws -> [JSON] {
        var items: [JSON] = []
        var next: String? = first
        while let url = next, items.count < max {
            let page = try await get(url)
            items += page.objects("items")
            next = page.string("next")
        }
        return items
    }

    static func me() async throws -> JSON { try await get("/me") }

    static func myPlaylists() async throws -> [MusicList] {
        var seen = Set<String>()
        // Spotify can list one playlist twice across pages; show it once.
        let unique = try await pages("/me/playlists?limit=50").map(parsePlaylist).filter { seen.insert($0.uri).inserted }
        return unique.enumerated().map { i, playlist in
            var p = playlist
            p.order = i
            return p
        }
    }

    static func savedAlbums() async throws -> [MusicList] {
        var seen = Set<String>()
        return try await pages("/me/albums?limit=50", max: 500).enumerated().compactMap { i, saved in
            guard let o = saved.object("album") else { return nil }
            var album = parseAlbum(o)
            album.order = i
            album.addedAt = saved.string("added_at")
            return seen.insert(album.uri).inserted ? album : nil
        }
    }

    /// Entries that carry the song with the time it was added, numbered from `start`.
    static func saved(_ entries: [JSON], start: Int = 0) -> [Song] {
        let songs: [Song] = entries.compactMap { entry in
            // The current API names the song `item`; older answers say `track`.
            guard let o = entry.object("item") ?? entry.object("track"), var song = parseSong(o) else { return nil }
            song.addedAt = entry.string("added_at")
            return song
        }
        return songs.enumerated().map { i, song in
            var s = song
            s.order = start + i
            return s
        }
    }

    /// When each playlist or album was last played from, out of the last 50 plays.
    static func recentlyPlayedContexts() async throws -> [String: String] {
        var played: [String: String] = [:]
        for play in try await get("/me/player/recently-played?limit=50").objects("items") {
            guard let uri = play.object("context")?.string("uri") else { continue }
            let at = play.text("played_at")
            if at > (played[uri] ?? "") { played[uri] = at }
        }
        return played
    }

    static func albumSongs(_ id: String) async throws -> [Song] {
        let album = try await get("/albums/\(id)")
        let cover = imageUrl(album.objects("images"))
        var items = album.object("tracks")?.objects("items") ?? []
        if let next = album.object("tracks")?.string("next") { items += try await pages(next) }
        return items.compactMap { o -> Song? in
            guard var song = parseSong(o, albumImage: cover) else { return nil }
            song.album = album.text("name")
            song.releaseDate = album.string("release_date")
            return song
        }.enumerated().map { i, song in
            var s = song
            s.order = i
            return s
        }
    }

    static func artistTopSongs(_ id: String) async throws -> [Song] {
        try await get("/artists/\(id)/top-tracks").objects("tracks").compactMap { parseSong($0) }
            .enumerated().map { i, song in
                var s = song
                s.order = i
                return s
            }
    }

    static func search(_ query: String) async throws -> SearchResults {
        let q = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed.subtracting(CharacterSet(charactersIn: "&+=?"))) ?? query
        let o = try await get("/search?q=\(q)&type=track,album,artist,playlist&limit=10")
        return SearchResults(
            songs: o.object("tracks")?.objects("items").compactMap { parseSong($0) } ?? [],
            albums: o.object("albums")?.objects("items").map(parseAlbum) ?? [],
            artists: o.object("artists")?.objects("items").map(parseArtist) ?? [],
            playlists: o.object("playlists")?.objects("items").map(parsePlaylist) ?? []
        )
    }

    /// The songs of `collection`, page by page: `onPage` gets every song read
    /// so far and the total, so a long playlist shows its first songs at once.
    static func streamSongs(_ collection: MusicList, onPage: @MainActor ([Song], Int) -> Void) async throws {
        let first: String
        switch collection.kind {
        case .playlist: first = "/playlists/\(collection.spotifyId)/items?limit=100&additional_types=track,episode"
        case .liked: first = "/me/tracks?limit=50"
        case .album:
            let all = try await albumSongs(collection.spotifyId)
            await onPage(all, all.count)
            return
        case .artist:
            let all = try await artistTopSongs(collection.spotifyId)
            await onPage(all, all.count)
            return
        }
        var read: [Song] = []
        var next: String? = first
        while let url = next {
            let page = try await get(url)
            read += saved(page.objects("items"), start: read.count)
            let total = page.int("total")
            await onPage(read, total > 0 ? total : read.count)
            next = page.string("next")
        }
    }
}
