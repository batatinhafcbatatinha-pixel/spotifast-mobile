import SafariServices
import SwiftUI

let redirectURI = "http://127.0.0.1:8989/login"

extension URL: @retroactive Identifiable {
    public var id: String { absoluteString }
}

// ----- Root -----

struct RootView: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let p = makePalette(app.appearance, accent: app.accent, systemDark: scheme == .dark, cover: app.cover)
        ZStack {
            p.background.ignoresSafeArea()
            if app.status.webSignedIn {
                MainView()
            } else {
                SetupView()
            }
            if let busy = app.busy, app.loginURL == nil {
                BusyOverlay(message: busy)
            }
        }
        .environment(\.palette, p)
        .fontDesign(p.design)
        .tint(p.accent)
        .foregroundStyle(p.text)
        .preferredColorScheme(app.appearance == .system ? nil : (p.dark ? .dark : .light))
        .sheet(item: $app.loginURL, onDismiss: { app.cancelLogin() }) { url in
            SafariView(url: url).ignoresSafeArea()
        }
        .fullScreenCover(isPresented: $app.showNowPlaying) {
            NowPlayingView()
                .environmentObject(app)
                .environment(\.palette, p)
                .fontDesign(p.design)
                .tint(p.accent)
                .foregroundStyle(p.text)
                .preferredColorScheme(app.appearance == .system ? nil : (p.dark ? .dark : .light))
        }
        .alert("Spotifast", isPresented: Binding(get: { app.error != nil }, set: { if !$0 { app.error = nil } })) {
            Button("OK", role: .cancel) { app.error = nil }
        } message: {
            Text(app.error ?? "")
        }
    }
}

struct SafariView: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context: Context) -> SFSafariViewController { SFSafariViewController(url: url) }
    func updateUIViewController(_ controller: SFSafariViewController, context: Context) {}
}

struct BusyOverlay: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.palette) private var p
    let message: String

    var body: some View {
        ZStack {
            p.background.opacity(0.85).ignoresSafeArea()
            VStack(spacing: 16) {
                ProgressView().controlSize(.large)
                Text(message)
                Button("Cancel") { app.cancelLogin() }
            }
        }
    }
}

// ----- Sign-in -----

struct SetupView: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.palette) private var p
    @State private var clientId = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Circle().fill(p.accent).frame(width: 64, height: 64)
                    .overlay(Image(systemName: "play.fill").font(.title).foregroundStyle(p.onAccent))
                    .padding(.top, 24)
                Text("Spotifast").font(.largeTitle.bold())
                Text("Spotify, light and fast. Playback needs Spotify Premium.").foregroundStyle(p.muted)
                VStack(alignment: .leading, spacing: 8) {
                    Text("1. Register Spotifast with Spotify").fontWeight(.semibold)
                    Text("In the Spotify developer dashboard (developer.spotify.com/dashboard), choose Create app, tick Web API, and add this redirect URI:")
                        .foregroundStyle(p.muted)
                    HStack {
                        Text(redirectURI).foregroundStyle(p.accent).textSelection(.enabled)
                        Spacer()
                        Button("Copy") { UIPasteboard.general.string = redirectURI }
                    }
                    Text("Already use Spotifast elsewhere? The same Client ID works here.")
                        .font(.footnote).foregroundStyle(p.muted)
                }
                .padding()
                .background(p.surface, in: RoundedRectangle(cornerRadius: 12))
                Text("2. Paste the Client ID").fontWeight(.semibold)
                TextField("Client ID", text: $clientId)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .padding(12)
                    .background(p.surface, in: RoundedRectangle(cornerRadius: 10))
                    .submitLabel(.go)
                    .onSubmit { if clientId.count == 32 { app.signIn(clientId: clientId) } }
                Button {
                    app.signIn(clientId: clientId)
                } label: {
                    Text("Sign in with Spotify").frame(maxWidth: .infinity).padding(.vertical, 6)
                }
                .buttonStyle(.borderedProminent)
                .foregroundStyle(p.onAccent)
                .disabled(clientId.trimmingCharacters(in: .whitespaces).count != 32)
            }
            .padding(24)
        }
        .onAppear { clientId = app.status.clientId ?? clientId }
    }
}

// ----- Main -----

struct MainView: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.palette) private var p

    var body: some View {
        VStack(spacing: 0) {
            NavigationStack(path: $app.stack) {
                Group {
                    switch app.tab {
                    case .library: LibraryView()
                    case .search: SearchView()
                    }
                }
                .navigationDestination(for: MusicList.self) { CollectionView(collection: $0) }
            }
            MiniPlayer()
            HStack {
                tabButton(.library, "Library", "music.note.list")
                tabButton(.search, "Search", "magnifyingglass")
            }
            .padding(.top, 6)
            .background(p.surface.ignoresSafeArea(edges: .bottom))
        }
    }

    private func tabButton(_ tab: Tab, _ title: String, _ icon: String) -> some View {
        Button {
            app.stack = []
            app.tab = tab
        } label: {
            VStack(spacing: 2) {
                Image(systemName: icon).font(.title3)
                Text(title).font(.caption)
            }
            .frame(maxWidth: .infinity)
            .foregroundStyle(app.tab == tab ? p.accent : p.muted)
        }
    }
}

struct Cover: View {
    @Environment(\.palette) private var p
    let url: String?
    let size: CGFloat
    var round = false
    var liked = false

    var body: some View {
        ZStack {
            if liked {
                LinearGradient(colors: [Color(hex: 0x4A2BD8), Color(hex: 0x9AC7C2)], startPoint: .topLeading, endPoint: .bottomTrailing)
                Image(systemName: "heart.fill").foregroundStyle(.white)
            } else if let url, let link = URL(string: url) {
                AsyncImage(url: link) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    p.surface
                }
            } else {
                p.surface
                Image(systemName: "music.note").foregroundStyle(p.muted)
            }
        }
        .frame(width: size, height: size)
        .clipShape(round ? AnyShape(Circle()) : AnyShape(RoundedRectangle(cornerRadius: 6)))
    }
}

struct CollectionRow: View {
    @Environment(\.palette) private var p
    let collection: MusicList

    var body: some View {
        HStack(spacing: 12) {
            Cover(url: collection.imageUrl, size: 56, round: collection.kind == .artist, liked: collection.kind == .liked)
            VStack(alignment: .leading, spacing: 2) {
                Text(collection.name).lineLimit(1)
                Text(collection.subtitle).font(.footnote).foregroundStyle(p.muted).lineLimit(1)
            }
            Spacer()
        }
        .contentShape(Rectangle())
    }
}

struct SongRow: View {
    @Environment(\.palette) private var p
    let song: Song
    let current: Bool
    let onTap: () -> Void
    let onQueue: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Cover(url: song.imageUrl, size: 48)
            VStack(alignment: .leading, spacing: 2) {
                Text(song.name).lineLimit(1)
                    .foregroundStyle(current ? p.accent : (song.playable ? p.text : p.muted))
                Text(song.artists).font(.footnote).foregroundStyle(p.muted).lineLimit(1)
            }
            Spacer()
            Menu {
                Button { onQueue() } label: { Label("Add to queue", systemImage: "text.badge.plus") }
            } label: {
                Image(systemName: "ellipsis").foregroundStyle(p.muted).padding(8)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { if song.playable { onTap() } }
    }
}

private struct Chip: View {
    @Environment(\.palette) private var p
    let title: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title).font(.subheadline)
                .padding(.horizontal, 14).padding(.vertical, 7)
                .background(selected ? p.raised : Color.clear, in: Capsule())
                .overlay(Capsule().stroke(p.track, lineWidth: selected ? 0 : 1))
                .foregroundStyle(p.text)
        }
    }
}

private struct FilterField: View {
    @Environment(\.palette) private var p
    let hint: String
    @Binding var text: String

    var body: some View {
        HStack {
            Image(systemName: "magnifyingglass").foregroundStyle(p.muted)
            TextField(hint, text: $text).autocorrectionDisabled()
            if !text.isEmpty {
                Button { text = "" } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(p.muted) }
            }
        }
        .padding(10)
        .background(p.surface, in: RoundedRectangle(cornerRadius: 10))
    }
}

// ----- Library -----

struct LibraryView: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.palette) private var p
    @State private var filtering = false
    @State private var themes = false

    var body: some View {
        let shown = app.library
        let showLiked = app.libraryShow != .albums && app.libraryShow != .mine &&
            (app.libraryText.isEmpty || "liked songs".contains(app.libraryText.lowercased()))
        List {
            Section {
                if filtering {
                    FilterField(hint: "Filter playlists and albums", text: $app.libraryText)
                }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(LibraryShow.allCases) { show in
                            Chip(title: show.label, selected: app.libraryShow == show) {
                                app.libraryShow = app.libraryShow == show ? .all : show
                            }
                        }
                    }
                }
                Menu {
                    Picker("Sort", selection: $app.librarySort) {
                        ForEach(LibrarySort.allCases) { Text($0.label).tag($0) }
                    }
                } label: {
                    Label(app.librarySort.label, systemImage: "arrow.up.arrow.down").font(.subheadline)
                }
                if app.libraryLoading { ProgressView().frame(maxWidth: .infinity) }
                if !app.status.playbackSignedIn {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Play music on this phone").fontWeight(.semibold)
                        Text("Approve playback once in the browser. This phone then shows up in Spotify Connect as Spotifast.")
                            .foregroundStyle(p.muted)
                        Button("Set up playback here") { app.setUpPlayback() }
                            .buttonStyle(.borderedProminent)
                            .foregroundStyle(p.onAccent)
                    }
                    .padding()
                    .background(p.surface, in: RoundedRectangle(cornerRadius: 12))
                }
                if showLiked {
                    CollectionRow(collection: app.liked).onTapGesture { app.open(app.liked) }
                }
                ForEach(Array(shown.enumerated()), id: \.offset) { _, c in
                    CollectionRow(collection: c).onTapGesture { app.open(c) }
                }
                if shown.isEmpty && !app.libraryLoading {
                    Text("Nothing matches.").foregroundStyle(p.muted)
                }
            }
            .listRowBackground(p.background)
            .listRowSeparator(.hidden)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(p.background)
        .navigationTitle("Your Library")
        .toolbarBackground(p.background, for: .navigationBar)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button {
                    filtering.toggle()
                    if !filtering { app.libraryText = "" }
                } label: { Image(systemName: "magnifyingglass") }
                Menu {
                    Button("Refresh") { app.loadLibrary() }
                    Button("Play what's on my other device here") { app.transfer() }
                    Button("Theme") { themes = true }
                    Button("Sign out", role: .destructive) { app.signOut() }
                } label: { Image(systemName: "ellipsis.circle") }
            }
        }
        .sheet(isPresented: $themes) { ThemeSheet().environmentObject(app).environment(\.palette, p) }
    }
}

// ----- A playlist, album, artist or Liked Songs -----

struct CollectionView: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.palette) private var p
    let collection: MusicList
    @State private var filtering = false

    var body: some View {
        let all = app.songs[collection.uri]
        let songs = app.shownSongs(collection) ?? []
        let loading = app.loading(collection)
        List {
            Section {
                VStack(spacing: 10) {
                    Cover(url: collection.imageUrl, size: 180, round: collection.kind == .artist, liked: collection.kind == .liked)
                    Text(collection.name).font(.title2.bold()).multilineTextAlignment(.center)
                    Text(collection.subtitle).foregroundStyle(p.muted)
                    HStack(spacing: 12) {
                        Button { app.playShown(collection, songs, from: nil) } label: {
                            Label("Play", systemImage: "play.fill")
                        }
                        .buttonStyle(.borderedProminent)
                        .foregroundStyle(p.onAccent)
                        Button { app.playShown(collection, songs, from: nil, shuffle: true) } label: {
                            Label("Shuffle", systemImage: "shuffle")
                        }
                        .buttonStyle(.bordered)
                    }
                    .disabled(songs.isEmpty)
                }
                .frame(maxWidth: .infinity)
                if filtering {
                    FilterField(hint: "Filter by title, artist or album", text: $app.songFilter.text)
                }
                if let all {
                    controls(all: all, shownCount: songs.count, loading: loading)
                } else {
                    ProgressView().frame(maxWidth: .infinity)
                }
                ForEach(Array(songs.enumerated()), id: \.offset) { index, song in
                    SongRow(song: song, current: song.uri == app.player.track?.uri,
                            onTap: { app.playShown(collection, songs, from: index) },
                            onQueue: { app.queue(song) })
                }
            }
            .listRowBackground(p.background)
            .listRowSeparator(.hidden)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(p.background)
        .navigationTitle(collection.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(p.background, for: .navigationBar)
        .toolbar {
            Button {
                filtering.toggle()
                if !filtering { app.songFilter.text = "" }
            } label: { Image(systemName: "magnifyingglass") }
        }
    }

    @ViewBuilder
    private func controls(all: [Song], shownCount: Int, loading: (Int, Int)?) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Menu {
                    Picker("Sort", selection: $app.songSort) {
                        ForEach(songSorts(all)) { Text($0.label).tag($0) }
                    }
                } label: {
                    Label(app.songSort.label, systemImage: "arrow.up.arrow.down").font(.subheadline)
                }
                Spacer()
                Text(loading.map { "Loading \($0.0) of \($0.1)…" }
                    ?? (app.songFilter.active ? "\(shownCount) of \(all.count)" : "\(all.count) songs"))
                    .font(.footnote).foregroundStyle(p.muted)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    Menu {
                        Picker("Length", selection: $app.songFilter.length) {
                            ForEach(LengthFilter.allCases) { Text($0.label).tag($0) }
                        }
                    } label: {
                        Text(app.songFilter.length.label).font(.subheadline)
                            .padding(.horizontal, 14).padding(.vertical, 7)
                            .background(app.songFilter.length != .any ? p.raised : Color.clear, in: Capsule())
                            .overlay(Capsule().stroke(p.track, lineWidth: app.songFilter.length != .any ? 0 : 1))
                            .foregroundStyle(p.text)
                    }
                    if all.contains(where: \.explicit) {
                        Chip(title: "Hide explicit", selected: app.songFilter.hideExplicit) {
                            app.songFilter.hideExplicit.toggle()
                        }
                    }
                    if app.songFilter.active {
                        Button("Clear filters") { app.songFilter = SongFilter() }.font(.subheadline)
                    }
                }
            }
            if let loading {
                ProgressView(value: Double(loading.0), total: Double(max(loading.1, 1)))
            }
            if shownCount == 0 && !all.isEmpty {
                Text("No songs match these filters.").foregroundStyle(p.muted)
            }
        }
    }
}

// ----- Search -----

struct SearchView: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.palette) private var p

    var body: some View {
        let r = app.results
        let playing = app.player.track?.uri
        List {
            Section {
                FilterField(hint: "What do you want to listen to?", text: Binding(get: { app.query }, set: { app.search($0) }))
                if app.searching { ProgressView().frame(maxWidth: .infinity) }
            }
            .listRowBackground(p.background)
            .listRowSeparator(.hidden)
            if !r.songs.isEmpty {
                Section("Songs") {
                    ForEach(Array(r.songs.enumerated()), id: \.offset) { _, song in
                        SongRow(song: song, current: song.uri == playing,
                                onTap: { app.play(nil, song) }, onQueue: { app.queue(song) })
                    }
                }
                .listRowBackground(p.background).listRowSeparator(.hidden)
            }
            results("Artists", r.artists)
            results("Albums", r.albums)
            results("Playlists", r.playlists)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(p.background)
        .navigationTitle("Search")
        .toolbarBackground(p.background, for: .navigationBar)
    }

    @ViewBuilder
    private func results(_ title: String, _ items: [MusicList]) -> some View {
        if !items.isEmpty {
            Section(title) {
                ForEach(Array(items.enumerated()), id: \.offset) { _, c in
                    CollectionRow(collection: c).onTapGesture { app.open(c) }
                }
            }
            .listRowBackground(p.background)
            .listRowSeparator(.hidden)
        }
    }
}

// ----- Player -----

/// The position now, refreshed while visible.
struct PositionReader<Content: View>: View {
    @EnvironmentObject var app: AppModel
    @ViewBuilder let content: (Int) -> Content

    var body: some View {
        TimelineView(.periodic(from: .now, by: 0.25)) { _ in
            content(app.player.positionNow())
        }
    }
}

struct MiniPlayer: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.palette) private var p

    var body: some View {
        if let track = app.player.track {
            VStack(spacing: 0) {
                HStack(spacing: 10) {
                    Cover(url: track.artUrl, size: 40)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(track.title).fontWeight(.semibold).lineLimit(1)
                        Text(track.artists).font(.footnote).foregroundStyle(p.muted).lineLimit(1)
                    }
                    Spacer()
                    if app.player.loading {
                        ProgressView()
                    } else {
                        Button { app.toggle() } label: {
                            Image(systemName: app.player.playing ? "pause.fill" : "play.fill").font(.title3)
                        }
                        .accessibilityLabel("Play or pause")
                    }
                    Button { app.next() } label: { Image(systemName: "forward.fill").font(.title3) }
                        .accessibilityLabel("Next")
                }
                .foregroundStyle(p.text)
                .padding(8)
                PositionReader { position in
                    ProgressView(value: Double(position), total: Double(max(track.durationMs, 1)))
                        .tint(p.accent)
                }
            }
            .background(p.raised, in: RoundedRectangle(cornerRadius: 8))
            .padding(.horizontal, 8)
            .padding(.bottom, 4)
            .onTapGesture { app.showNowPlaying = true }
        }
    }
}

private func time(_ ms: Int) -> String {
    let s = ms / 1000
    return String(format: "%d:%02d", s / 60, s % 60)
}

struct NowPlayingView: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.palette) private var p
    @State private var dragging: Double?

    var body: some View {
        let state = app.player
        let track = state.track
        let duration = Double(max(track?.durationMs ?? 1, 1))
        ZStack {
            p.background.ignoresSafeArea()
            VStack(spacing: 16) {
                HStack {
                    Button { app.showNowPlaying = false } label: { Image(systemName: "chevron.down").font(.title3) }
                    Text(state.activeClient.isEmpty ? "Now playing" : "Playing from \(state.activeClient)")
                        .font(.subheadline).foregroundStyle(p.muted)
                    Spacer()
                }
                Cover(url: track?.artUrl, size: UIScreen.main.bounds.width - 48)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                VStack(alignment: .leading, spacing: 4) {
                    Text(track?.title ?? "Nothing playing").font(.title2.bold()).lineLimit(1)
                    Text(track?.artists ?? "").foregroundStyle(p.muted).lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                PositionReader { position in
                    VStack(spacing: 4) {
                        Slider(
                            value: Binding(get: { dragging ?? min(Double(position) / duration, 1) }, set: { dragging = $0 }),
                            in: 0...1,
                            onEditingChanged: { editing in
                            if !editing, let value = dragging {
                                app.seek(Int(value * duration))
                                dragging = nil
                            }
                        })
                        .tint(p.accent)
                        HStack {
                            Text(time(dragging.map { Int($0 * duration) } ?? position))
                            Spacer()
                            Text(time(track?.durationMs ?? 0))
                        }
                        .font(.caption).foregroundStyle(p.muted)
                    }
                }
                HStack {
                    Button { app.setShuffle(!state.shuffle) } label: {
                        Image(systemName: "shuffle").foregroundStyle(state.shuffle ? p.accent : p.muted)
                    }
                    Spacer()
                    Button { app.previous() } label: { Image(systemName: "backward.fill").font(.title) }
                    Spacer()
                    Button { app.toggle() } label: {
                        ZStack {
                            Circle().fill(p.accent).frame(width: 72, height: 72)
                            if state.loading {
                                ProgressView().tint(p.onAccent)
                            } else {
                                Image(systemName: state.playing ? "pause.fill" : "play.fill")
                                    .font(.largeTitle).foregroundStyle(p.onAccent)
                            }
                        }
                    }
                    Spacer()
                    Button { app.next() } label: { Image(systemName: "forward.fill").font(.title) }
                    Spacer()
                    Button { app.cycleRepeat() } label: {
                        Image(systemName: state.repeatMode == "track" ? "repeat.1" : "repeat")
                            .foregroundStyle(state.repeatMode == "off" ? p.muted : p.accent)
                    }
                }
                .foregroundStyle(p.text)
                if let error = state.error {
                    Text(error).foregroundStyle(.red)
                }
                Spacer()
            }
            .padding(24)
        }
    }
}

// ----- Theme -----

struct ThemeSheet: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.palette) private var p
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(Appearance.allCases) { appearance in
                        Button {
                            app.appearance = appearance
                        } label: {
                            HStack {
                                Text(appearance.label).foregroundStyle(p.text)
                                Spacer()
                                if app.appearance == appearance {
                                    Image(systemName: "checkmark").foregroundStyle(p.accent)
                                }
                            }
                        }
                    }
                }
                if app.appearance.takesAccent {
                    Section("Accent colour") {
                        HStack(spacing: 12) {
                            ForEach(Accent.allCases) { accent in
                                Circle()
                                    .fill(p.dark ? accent.dark : accent.light)
                                    .frame(width: 36, height: 36)
                                    .overlay {
                                        if app.accent == accent {
                                            Image(systemName: "checkmark").foregroundStyle(.white)
                                        }
                                    }
                                    .onTapGesture { app.accent = accent }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Theme")
            .toolbar { Button("Done") { dismiss() } }
        }
    }
}
