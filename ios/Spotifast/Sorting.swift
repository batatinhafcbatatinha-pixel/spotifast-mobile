import Foundation

/// Sorting and filtering, done on the phone over what Spotify already sent:
/// the Web API takes no sort parameters for playlists, albums or items.

enum LibrarySort: String, CaseIterable, Identifiable {
    case recent, played, name, nameDesc, creator, mostSongs, fewestSongs
    var id: String { rawValue }
    var label: String {
        switch self {
        case .recent: "Recently added"
        case .played: "Recently played"
        case .name: "Name (A–Z)"
        case .nameDesc: "Name (Z–A)"
        case .creator: "Creator"
        case .mostSongs: "Most songs"
        case .fewestSongs: "Fewest songs"
        }
    }
}

enum LibraryShow: String, CaseIterable, Identifiable {
    case all, playlists, albums, mine
    var id: String { rawValue }
    var label: String {
        switch self {
        case .all: "All"
        case .playlists: "Playlists"
        case .albums: "Albums"
        case .mine: "By you"
        }
    }
}

enum SongSort: String, CaseIterable, Identifiable {
    case standard, addedNew, addedOld, title, artist, album, shortest, longest, newest, oldest, popular
    var id: String { rawValue }
    var label: String {
        switch self {
        case .standard: "Default order"
        case .addedNew: "Recently added"
        case .addedOld: "Oldest added"
        case .title: "Title (A–Z)"
        case .artist: "Artist (A–Z)"
        case .album: "Album (A–Z)"
        case .shortest: "Shortest first"
        case .longest: "Longest first"
        case .newest: "Newest releases"
        case .oldest: "Oldest releases"
        case .popular: "Most popular"
        }
    }
}

enum LengthFilter: String, CaseIterable, Identifiable {
    case any, brief, medium, lengthy
    var id: String { rawValue }
    var label: String {
        switch self {
        case .any: "Any length"
        case .brief: "Under 3 min"
        case .medium: "3–5 min"
        case .lengthy: "Over 5 min"
        }
    }
    func contains(_ ms: Int) -> Bool {
        switch self {
        case .any: true
        case .brief: ms < 180_000
        case .medium: (180_000...300_000).contains(ms)
        case .lengthy: ms > 300_000
        }
    }
}

struct SongFilter: Equatable {
    var text = ""
    var length = LengthFilter.any
    var hideExplicit = false
    var active: Bool { !text.trimmingCharacters(in: .whitespaces).isEmpty || length != .any || hideExplicit }
}

private func key(_ text: String) -> String { text.trimmingCharacters(in: .whitespaces).lowercased() }

private func matches(_ haystack: String, _ needle: String) -> Bool {
    needle.trimmingCharacters(in: .whitespaces).isEmpty || key(haystack).contains(key(needle))
}

func arrangeLibrary(
    _ items: [MusicList], sort: LibrarySort, show: LibraryShow, text: String,
    myId: String?, played: [String: String]
) -> [MusicList] {
    let shown = items.filter { c in
        let kindOk: Bool = switch show {
        case .all: true
        case .playlists: c.kind == .playlist
        case .albums: c.kind == .album
        case .mine: c.kind == .playlist && c.ownerId != nil && c.ownerId == myId
        }
        return kindOk && (matches(c.name, text) || matches(c.subtitle, text))
    }
    switch sort {
    case .recent:
        // Playlists in library order first, then albums by when they were saved.
        return shown.sorted {
            if ($0.kind == .playlist) != ($1.kind == .playlist) { return $0.kind == .playlist }
            if ($0.addedAt ?? "") != ($1.addedAt ?? "") { return ($0.addedAt ?? "") > ($1.addedAt ?? "") }
            return $0.order < $1.order
        }
    case .played:
        return shown.sorted {
            let a = played[$0.uri] ?? "", b = played[$1.uri] ?? ""
            if a != b { return a > b }
            if ($0.kind == .playlist) != ($1.kind == .playlist) { return $0.kind == .playlist }
            return $0.order < $1.order
        }
    case .name: return shown.sorted { key($0.name) < key($1.name) }
    case .nameDesc: return shown.sorted { key($0.name) > key($1.name) }
    case .creator: return shown.sorted { (key($0.subtitle), key($0.name)) < (key($1.subtitle), key($1.name)) }
    case .mostSongs: return shown.sorted { $0.total > $1.total }
    case .fewestSongs: return shown.sorted { $0.total < $1.total }
    }
}

func arrangeSongs(_ songs: [Song], sort: SongSort, filter: SongFilter) -> [Song] {
    let shown = songs.filter { s in
        (matches(s.name, filter.text) || matches(s.artists, filter.text) || matches(s.album, filter.text)) &&
            filter.length.contains(s.durationMs) && !(filter.hideExplicit && s.explicit)
    }
    switch sort {
    case .standard: return shown.sorted { $0.order < $1.order }
    case .addedNew: return shown.sorted { ($0.addedAt ?? "") > ($1.addedAt ?? "") }
    case .addedOld: return shown.sorted { ($0.addedAt ?? "\u{FFFF}") < ($1.addedAt ?? "\u{FFFF}") }
    case .title: return shown.sorted { key($0.name) < key($1.name) }
    case .artist: return shown.sorted { (key($0.artists), key($0.album), $0.order) < (key($1.artists), key($1.album), $1.order) }
    case .album: return shown.sorted { (key($0.album), $0.order) < (key($1.album), $1.order) }
    case .shortest: return shown.sorted { $0.durationMs < $1.durationMs }
    case .longest: return shown.sorted { $0.durationMs > $1.durationMs }
    case .newest: return shown.sorted { ($0.releaseDate ?? "") > ($1.releaseDate ?? "") }
    case .oldest: return shown.sorted { ($0.releaseDate ?? "\u{FFFF}") < ($1.releaseDate ?? "\u{FFFF}") }
    case .popular: return shown.sorted { $0.popularity > $1.popularity }
    }
}

/// Sorts that only make sense when Spotify sent the data behind them.
func songSorts(_ songs: [Song]) -> [SongSort] {
    SongSort.allCases.filter { sort in
        switch sort {
        case .addedNew, .addedOld: songs.contains { $0.addedAt != nil }
        case .newest, .oldest: songs.contains { $0.releaseDate != nil }
        case .popular: songs.contains { $0.popularity > 0 }
        default: true
        }
    }
}
