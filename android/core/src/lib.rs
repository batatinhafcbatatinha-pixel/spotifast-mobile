//! Spotifast's core for Android and iOS.
//!
//! The desktop app keeps sign-in, the Web API and librespot inside one egui
//! binary. On Android the interface, the media session and the foreground
//! service are Kotlin; this library is everything below them, reached over
//! a small JNI surface (`io.github.batatinha.spotifast.Native`) that trades
//! JSON strings so the Kotlin side owns its own models. iOS reaches the
//! same calls through a C ABI (`ffi`), built as a static library.
//!
//! Sign-in reuses the desktop's `auth.rs` as is: the same PKCE flow and the
//! same loopback redirect URIs, so a Spotify app registered for the desktop
//! client works here unchanged.

#[path = "../../../src/auth.rs"]
#[allow(dead_code)]
mod auth;
mod core;
mod engine;
#[cfg(not(target_os = "android"))]
mod ffi;
#[cfg(target_os = "android")]
mod jni_api;

pub use crate::core::Core;
