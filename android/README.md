# Spotifast for Android

An Android port of Spotifast: a small Spotify client with a Rust core and a
native Jetpack Compose interface. **Playback needs Spotify Premium.**

> Experimental, personal fork. Not affiliated with the upstream project
> (which supports desktop only) or with Spotify.

## How it is built

```
android/
├── core/   Rust: sign-in, Web API, librespot playback (cdylib, JNI)
└── app/    Kotlin: Compose UI, Media3 session, foreground playback service
```

- **core** reuses the desktop app's `src/auth.rs` unchanged (PKCE sign-in with
  a loopback redirect) and the same patched librespot. It exposes a small JNI
  surface, `io.github.batatinha.spotifast.Native`, that trades JSON strings.
  Audio goes through rodio/cpal, which plays with AAudio.
- **app** is the interface and everything Android-specific: a
  `MediaSessionService` with a Media3 `SimpleBasePlayer` that mirrors the
  core's state, so the media notification, lock screen, Bluetooth and headset
  controls work and music keeps playing in the background. It also handles
  audio focus and pauses when headphones are unplugged.

## Features

- Library: Liked Songs, your playlists, saved albums
- Search: songs, artists, albums, playlists
- Playback on the phone through Spotify Connect: play, pause, skip, seek,
  shuffle, repeat, add to queue; the phone shows up as a Connect device
  other Spotify apps can control
- Take over what is playing on another device
- Mini player, a Now Playing screen, and the system media notification
- Sort and filter the library: recently added, recently played, name,
  creator, number of songs; show only playlists, albums or your own
- Sort and filter songs: recently added, title, artist, album, length,
  release date; filter by text, length, and hide explicit songs. Playing a
  sorted or filtered list plays it in the order shown
- Themes: follows the phone's dark or light mode by default; dark, light
  and AMOLED black with an accent colour; a dynamic theme coloured by the
  cover playing; and presets after popular Spicetify themes (Catppuccin
  Mocha and Latte, Dracula, Nord, Gruvbox, Fluent, Comfy, Throwback) plus
  80s synthwave, 90s vaporwave, Terminal, Cyberpunk, Coffee and Rose;
  and your own, imported from a JSON file or pasted (see
  [THEMES.md](../THEMES.md))
- Small and constant on storage: no audio or image cache on disk

Not yet: offline downloads (librespot cannot), lyrics, podcasts browsing,
playlist editing, Android Auto browsing.

## Getting started

1. In the [Spotify developer dashboard](https://developer.spotify.com/dashboard),
   create an app with **Web API** ticked and the redirect URI
   `http://127.0.0.1:8989/login`. A Client ID already made for desktop
   Spotifast works too.
2. Open the app, paste the Client ID and sign in.
3. Tap **Set up playback here** and approve playback in the browser.

## Building

Requirements: Rust (the version in `../rust-toolchain.toml`), the Android
targets, [cargo-ndk](https://github.com/bbqsrc/cargo-ndk), the Android SDK
(platform 36) and NDK, and a JDK 17 or newer.

```sh
rustup target add aarch64-linux-android x86_64-linux-android
cargo install cargo-ndk
export ANDROID_NDK_HOME=/path/to/Android/Sdk/ndk/<version>

cd android
./gradlew assembleRelease      # builds the Rust core first (cargoBuild)
./gradlew assembleDebug -PskipRust   # reuse the libraries already built
```

The APKs land in `app/build/outputs/apk/`. Release builds are signed with
the debug key for sideloading.
