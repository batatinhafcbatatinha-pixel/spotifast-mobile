import AVFoundation
import MediaPlayer
import SwiftUI
import UIKit

enum Tab { case library, search }

@MainActor
final class AppModel: ObservableObject {
    @Published private(set) var status = Status()
    /// What a long operation is doing, shown with a spinner.
    @Published private(set) var busy: String?
    @Published var error: String?
    /// A sign-in page to show in Safari.
    @Published var loginURL: URL?

    @Published var tab = Tab.library
    @Published var stack: [MusicList] = []
    @Published private(set) var songs: [String: [Song]] = [:]
    @Published private(set) var songsLoading: [String: (Int, Int)] = [:]
    @Published var showNowPlaying = false

    @Published private(set) var playlists: [MusicList] = []
    @Published private(set) var albums: [MusicList] = []
    @Published private(set) var libraryLoading = false
    @Published private(set) var played: [String: String] = [:]

    @Published var query = ""
    @Published private(set) var results = SearchResults()
    @Published private(set) var searching = false
    private var searchTask: Task<Void, Never>?

    @Published private(set) var player = PlayerState()
    private var userId: String?

    // Remembered between launches.
    private let defaults = UserDefaults.standard
    @Published var librarySort: LibrarySort { didSet { defaults.set(librarySort.rawValue, forKey: "library_sort"); if librarySort == .played { loadPlayed() } } }
    @Published var libraryShow = LibraryShow.all
    @Published var libraryText = ""
    @Published var songSort: SongSort { didSet { defaults.set(songSort.rawValue, forKey: "song_sort") } }
    @Published var songFilter = SongFilter()
    @Published var appearance: Appearance { didSet { defaults.set(appearance.rawValue, forKey: "appearance"); coverURL = nil; followCover() } }
    @Published var accent: Accent { didSet { defaults.set(accent.rawValue, forKey: "accent") } }
    @Published private(set) var cover: CoverColors?
    private var coverURL: String?

    private let deviceName = "Spotifast (\(UIDevice.current.name))"
    private var poll: Timer?
    private var artworkURL: String?
    private var artwork: MPMediaItemArtwork?

    init() {
        let saved = UserDefaults.standard
        librarySort = LibrarySort(rawValue: saved.string(forKey: "library_sort") ?? "") ?? .recent
        songSort = SongSort(rawValue: saved.string(forKey: "song_sort") ?? "") ?? .standard
        appearance = Appearance(rawValue: saved.string(forKey: "appearance") ?? "") ?? .system
        accent = Accent(rawValue: saved.string(forKey: "accent") ?? "") ?? .green

        let files = FileManager.default
        let data = files.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("spotifast")
        let cache = files.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("spotifast")
        try? files.createDirectory(at: data, withIntermediateDirectories: true)
        do {
            try Core.initialize(dataDir: data.path, cacheDir: cache.path)
        } catch {
            self.error = error.localizedDescription
        }
        setUpRemoteCommands()
        observeAudioSession()

        Task {
            await refreshStatus()
            if status.webSignedIn { loadLibrary() }
            if status.playbackSignedIn { await connectQuietly() }
        }
        poll = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            Task { @MainActor in await self?.pollPlayer() }
        }
    }

    var liked: MusicList {
        MusicList(spotifyId: "liked", uri: "spotify:user:\(userId ?? "me"):collection",
                   name: "Liked Songs", subtitle: "Your saved songs", imageUrl: nil, kind: .liked)
    }

    var library: [MusicList] {
        arrangeLibrary(playlists + albums, sort: librarySort, show: libraryShow, text: libraryText, myId: userId, played: played)
    }

    func shownSongs(_ c: MusicList) -> [Song]? {
        songs[c.uri].map { arrangeSongs($0, sort: songSort, filter: songFilter) }
    }

    func loading(_ c: MusicList) -> (Int, Int)? { songsLoading[c.uri] }

    private func refreshStatus() async {
        if let text = try? await background({ try Core.status() }) {
            status = Status(parseJSON(text))
        }
    }

    private func failed(_ error: Error) {
        let message = error.localizedDescription
        // Cancelling is the user's own choice, not an error to report.
        if message != "sign-in cancelled" { self.error = message }
    }

    // ----- Sign-in -----

    func signIn(clientId: String) {
        let id = clientId.trimmingCharacters(in: .whitespaces)
        login("Waiting for Spotify…", begin: { try Core.beginWebLogin(clientId: id) }) { [weak self] in
            self?.loadLibrary()
        }
    }

    func setUpPlayback() {
        login("Waiting for playback approval…", begin: { try Core.beginPlaybackLogin() }) { [weak self] in
            guard let self else { return }
            self.busy = "Connecting playback…"
            Task {
                do { try await self.connect() } catch { self.failed(error) }
                self.busy = nil
                await self.refreshStatus()
            }
        }
    }

    private func login(_ message: String, begin: @escaping @Sendable () throws -> String, then: @escaping () -> Void) {
        error = nil
        Task {
            do {
                let url = try await background(begin)
                busy = message
                loginURL = URL(string: url)
                try await background { try Core.awaitLogin() }
                loginURL = nil
                busy = nil
                await refreshStatus()
                then()
            } catch {
                loginURL = nil
                busy = nil
                failed(error)
                await refreshStatus()
            }
        }
    }

    /// The sign-in page was closed, or Cancel tapped.
    func cancelLogin() {
        Core.cancelLogin()
        loginURL = nil
    }

    func signOut() {
        Task {
            try? await background { try Core.signOut() }
            playlists = []
            albums = []
            songs = [:]
            stack = []
            await refreshStatus()
        }
    }

    // ----- Library -----

    func loadLibrary() {
        libraryLoading = true
        Task {
            do {
                userId = try await Spotify.me().string("id")
                playlists = try await Spotify.myPlaylists()
                albums = try await Spotify.savedAlbums()
                loadPlayed()
            } catch {
                failed(error)
            }
            libraryLoading = false
        }
    }

    private func loadPlayed() {
        Task {
            if let recent = try? await Spotify.recentlyPlayedContexts() { played = recent }
        }
    }

    func open(_ c: MusicList) {
        songFilter = SongFilter()
        stack.append(c)
        if songs[c.uri] != nil { return }
        Task {
            do {
                try await Spotify.streamSongs(c) { [weak self] read, total in
                    self?.songs[c.uri] = read
                    self?.songsLoading[c.uri] = read.count < total ? (read.count, total) : nil
                }
            } catch {
                if songs[c.uri] == nil { songs[c.uri] = [] }
                failed(error)
            }
            songsLoading[c.uri] = nil
        }
    }

    func back() {
        if showNowPlaying {
            showNowPlaying = false
        } else if !stack.isEmpty {
            stack.removeLast()
        }
    }

    func search(_ text: String) {
        query = text
        searchTask?.cancel()
        if text.trimmingCharacters(in: .whitespaces).isEmpty {
            results = SearchResults()
            return
        }
        searchTask = Task {
            try? await Task.sleep(nanoseconds: 350_000_000)
            if Task.isCancelled { return }
            searching = true
            do {
                let found = try await Spotify.search(text)
                if !Task.isCancelled { results = found }
            } catch {
                if !Task.isCancelled { failed(error) }
            }
            searching = false
        }
    }

    // ----- Playback -----

    private func connect() async throws {
        let name = deviceName
        try await background { try Core.connect(deviceName: name) }
    }

    private func connectQuietly() async {
        do { try await connect() } catch { failed(error) }
        await refreshStatus()
    }

    private func send(_ command: [String: Any]) {
        Task {
            do {
                if !player.connected {
                    busy = "Connecting playback…"
                    defer { busy = nil }
                    try await connect()
                    await refreshStatus()
                }
                let json = command
                try await background { try Core.command(json) }
                await pollPlayer()
            } catch {
                failed(error)
                await refreshStatus()
            }
        }
    }

    /// Plays `song` within `collection`, or on its own when there is none.
    func play(_ collection: MusicList?, _ song: Song?, shuffle: Bool = false) {
        guard status.playbackSignedIn else {
            error = "Set up playback on this phone first."
            return
        }
        var command: [String: Any] = ["cmd": "load", "shuffle": shuffle]
        if let collection {
            command["context"] = collection.uri
            if let song { command["offset"] = song.uri }
        } else if let song {
            command["uris"] = [song.uri]
        }
        send(command)
    }

    /// Plays `shown` from `index`: the collection itself in Spotify's order,
    /// or, sorted or filtered, the songs as a list in the order on screen.
    func playShown(_ collection: MusicList, _ shown: [Song], from index: Int?, shuffle: Bool = false) {
        // An artist's context ignores the song chosen, so its top songs go as a list.
        if songSort == .standard && !songFilter.active && collection.kind != .artist {
            play(collection, index.map { shown[$0] }, shuffle: shuffle)
            return
        }
        guard status.playbackSignedIn else {
            error = "Set up playback on this phone first."
            return
        }
        let playable = shown.filter(\.playable)
        guard !playable.isEmpty else { return }
        let start = index.flatMap { i in playable.firstIndex(of: shown[i]) } ?? 0
        var command: [String: Any] = ["cmd": "load", "uris": playable.map(\.uri), "shuffle": shuffle]
        if !shuffle { command["index"] = start }
        send(command)
    }

    func toggle() { send(["cmd": "toggle"]) }
    func next() { send(["cmd": "next"]) }
    func previous() { send(["cmd": "prev"]) }
    func seek(_ ms: Int) { send(["cmd": "seek", "ms": ms]) }
    func setShuffle(_ on: Bool) { send(["cmd": "shuffle", "on": on]) }
    func cycleRepeat() {
        let next = switch player.repeatMode {
        case "off": "context"
        case "context": "track"
        default: "off"
        }
        send(["cmd": "repeat", "mode": next])
    }
    func queue(_ song: Song) { send(["cmd": "queue", "uri": song.uri]) }
    func transfer() { send(["cmd": "transfer"]) }

    private func pollPlayer() async {
        guard let text = try? await background({ try Core.playerState() }) else { return }
        let state = PlayerState(parseJSON(text))
        if state != player {
            let started = state.playing && !player.playing
            player = state
            if started { activateAudioSession() }
            updateNowPlaying()
            followCover()
        }
    }

    // ----- iOS media integration -----

    private func activateAudioSession() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .default)
        try? session.setActive(true)
    }

    private func observeAudioSession() {
        activateAudioSession()
        let center = NotificationCenter.default
        center.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] note in
            guard let raw = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
                  let type = AVAudioSession.InterruptionType(rawValue: raw) else { return }
            let options = (note.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt).map { AVAudioSession.InterruptionOptions(rawValue: $0) }
            Task { @MainActor in
                switch type {
                case .began: self?.send(["cmd": "pause"])
                case .ended:
                    if options?.contains(.shouldResume) == true {
                        self?.activateAudioSession()
                        self?.send(["cmd": "play"])
                    }
                @unknown default: break
                }
            }
        }
        center.addObserver(forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main) { [weak self] note in
            // Headphones unplugged: pause rather than play out loud.
            guard let raw = note.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt,
                  AVAudioSession.RouteChangeReason(rawValue: raw) == .oldDeviceUnavailable else { return }
            Task { @MainActor in self?.send(["cmd": "pause"]) }
        }
    }

    private func setUpRemoteCommands() {
        let center = MPRemoteCommandCenter.shared()
        center.playCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.send(["cmd": "play"]) }
            return .success
        }
        center.pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.send(["cmd": "pause"]) }
            return .success
        }
        center.togglePlayPauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.toggle() }
            return .success
        }
        center.nextTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.next() }
            return .success
        }
        center.previousTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.previous() }
            return .success
        }
        center.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            let ms = Int(event.positionTime * 1000)
            Task { @MainActor in self?.seek(ms) }
            return .success
        }
    }

    private func updateNowPlaying() {
        let center = MPNowPlayingInfoCenter.default()
        guard let track = player.track else {
            center.nowPlayingInfo = nil
            return
        }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: track.title,
            MPMediaItemPropertyArtist: track.artists,
            MPMediaItemPropertyAlbumTitle: track.album,
            MPMediaItemPropertyPlaybackDuration: Double(track.durationMs) / 1000,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: Double(player.positionNow()) / 1000,
            MPNowPlayingInfoPropertyPlaybackRate: player.playing ? 1.0 : 0.0,
        ]
        if artworkURL == track.artUrl, let artwork { info[MPMediaItemPropertyArtwork] = artwork }
        center.nowPlayingInfo = info
        center.playbackState = player.playing ? .playing : .paused
        if artworkURL != track.artUrl, let url = track.artUrl.flatMap(URL.init(string:)) {
            artworkURL = track.artUrl
            artwork = nil
            Task {
                guard let answer = try? await URLSession.shared.data(from: url), let image = UIImage(data: answer.0) else { return }
                artwork = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
                updateNowPlaying()
            }
        }
    }

    private func followCover() {
        guard appearance == .dynamic, let url = player.track?.artUrl, url != coverURL else { return }
        coverURL = url
        Task {
            guard let link = URL(string: url),
                  let answer = try? await URLSession.shared.data(from: link),
                  let image = UIImage(data: answer.0),
                  let colors = coverColors(of: image), coverURL == url else { return }
            cover = colors
        }
    }
}
