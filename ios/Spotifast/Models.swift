import Foundation

typealias JSON = [String: Any]

extension Dictionary where Key == String, Value == Any {
    func string(_ key: String) -> String? {
        guard let value = self[key] as? String, !value.isEmpty else { return nil }
        return value
    }
    func text(_ key: String) -> String { string(key) ?? "" }
    func int(_ key: String) -> Int { (self[key] as? NSNumber)?.intValue ?? 0 }
    func bool(_ key: String, _ fallback: Bool = false) -> Bool { (self[key] as? Bool) ?? fallback }
    func object(_ key: String) -> JSON? { self[key] as? JSON }
    func objects(_ key: String) -> [JSON] { (self[key] as? [Any])?.compactMap { $0 as? JSON } ?? [] }
}

func parseJSON(_ text: String) -> JSON {
    (try? JSONSerialization.jsonObject(with: Data(text.utf8))) as? JSON ?? [:]
}

struct Status {
    var webSignedIn = false
    var clientId: String?
    var playbackSignedIn = false
    var engineConnected = false

    init() {}
    init(_ o: JSON) {
        webSignedIn = o.bool("web_signed_in")
        clientId = o.string("client_id")
        playbackSignedIn = o.bool("playback_signed_in")
        engineConnected = o.bool("engine_connected")
    }
}

/// Anything that opens a list of songs: a playlist, an album, Liked Songs.
struct MusicList: Identifiable, Hashable {
    enum Kind { case playlist, album, liked, artist }

    var id: String { uri }
    let spotifyId: String
    let uri: String
    let name: String
    let subtitle: String
    let imageUrl: String?
    let kind: Kind
    /// Position in Spotify's own order.
    var order = 0
    var ownerId: String?
    var total = 0
    var addedAt: String?
    var releaseDate: String?
}

struct Song: Hashable {
    let uri: String
    let name: String
    let artists: String
    var album: String
    let imageUrl: String?
    let durationMs: Int
    let playable: Bool
    var order = 0
    var addedAt: String?
    var releaseDate: String?
    var explicit = false
    /// 0 to 100, or -1 when Spotify leaves it out.
    var popularity = -1
}

struct SearchResults {
    var songs: [Song] = []
    var albums: [MusicList] = []
    var artists: [MusicList] = []
    var playlists: [MusicList] = []
}

struct NowPlaying: Equatable {
    let uri: String
    let title: String
    let artists: String
    let album: String
    let artUrl: String?
    let durationMs: Int
}

struct PlayerState: Equatable {
    var connected = false
    var playing = false
    var loading = false
    var positionMs = 0
    /// Unix milliseconds when `positionMs` was true.
    var positionAt: Int64 = 0
    var track: NowPlaying?
    var shuffle = false
    var repeatMode = "off"
    var activeClient = ""
    var error: String?

    init() {}
    init(_ o: JSON) {
        connected = o.bool("connected")
        playing = o.bool("playing")
        loading = o.bool("loading")
        positionMs = o.int("position_ms")
        positionAt = (o["position_at"] as? NSNumber)?.int64Value ?? 0
        if let t = o.object("track") {
            track = NowPlaying(
                uri: t.text("uri"),
                title: t.text("title"),
                artists: ((t["artists"] as? [String]) ?? []).joined(separator: ", "),
                album: t.text("album"),
                artUrl: t.string("art_url"),
                durationMs: t.int("duration_ms")
            )
        }
        shuffle = o.bool("shuffle")
        repeatMode = o.string("repeat") ?? "off"
        activeClient = o.text("active_client")
        error = o.string("error")
    }

    /// The position now, carried forward from the last report while playing.
    func positionNow() -> Int {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let elapsed = playing ? max(0, Int(now - positionAt)) : 0
        let position = positionMs + elapsed
        if let track { return min(position, track.durationMs) }
        return position
    }
}

// ----- Web API parsing -----

/// The largest image that is still small enough for a row or a cover.
func imageUrl(_ images: [JSON]) -> String? {
    if images.isEmpty { return nil }
    let fit = images.first { (200...700).contains($0.int("width")) }
    return (fit ?? images[0]).string("url")
}

func artistNames(_ o: JSON) -> String {
    o.objects("artists").map { $0.text("name") }.joined(separator: ", ")
}

func parseSong(_ o: JSON, albumImage: String? = nil) -> Song? {
    guard let uri = o.string("uri") else { return nil }
    let album = o.object("album")
    var song = Song(
        uri: uri,
        name: o.text("name"),
        artists: artistNames(o).isEmpty ? (o.object("show")?.text("name") ?? "") : artistNames(o),
        album: album?.text("name") ?? "",
        imageUrl: imageUrl(album?.objects("images") ?? []) ?? imageUrl(o.objects("images")) ?? albumImage,
        durationMs: o.int("duration_ms"),
        playable: o.bool("is_playable", true) && !o.bool("is_local")
    )
    song.releaseDate = album?.string("release_date") ?? o.string("release_date")
    song.explicit = o.bool("explicit")
    if let popularity = o["popularity"] as? NSNumber { song.popularity = popularity.intValue }
    return song
}

func parsePlaylist(_ o: JSON) -> MusicList {
    var c = MusicList(
        spotifyId: o.text("id"), uri: o.text("uri"), name: o.text("name"),
        subtitle: o.object("owner")?.text("display_name") ?? "",
        imageUrl: imageUrl(o.objects("images")), kind: .playlist
    )
    c.ownerId = o.object("owner")?.string("id")
    // The current API counts songs under `items`; older answers say `tracks`.
    c.total = (o.object("items") ?? o.object("tracks"))?.int("total") ?? 0
    return c
}

func parseAlbum(_ o: JSON) -> MusicList {
    var c = MusicList(
        spotifyId: o.text("id"), uri: o.text("uri"), name: o.text("name"),
        subtitle: artistNames(o), imageUrl: imageUrl(o.objects("images")), kind: .album
    )
    c.total = o.int("total_tracks")
    c.releaseDate = o.string("release_date")
    return c
}

func parseArtist(_ o: JSON) -> MusicList {
    MusicList(
        spotifyId: o.text("id"), uri: o.text("uri"), name: o.text("name"),
        subtitle: "Artist", imageUrl: imageUrl(o.objects("images")), kind: .artist
    )
}
