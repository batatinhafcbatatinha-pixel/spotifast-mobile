import Foundation

/// A failure reported by the Rust core, with a message fit to show.
struct CoreError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

/// The Rust core (android/core) through its C ABI. Calls that touch the
/// network or wait for the browser block: make them through `background`.
enum Core {
    private static func call(_ body: () -> UnsafeMutablePointer<CChar>?) throws -> String {
        guard let pointer = body() else { throw CoreError(message: "The core did not answer.") }
        defer { spotifast_free(pointer) }
        let text = String(cString: pointer)
        guard let envelope = try JSONSerialization.jsonObject(with: Data(text.utf8)) as? [String: Any] else {
            throw CoreError(message: "The core answered something unreadable.")
        }
        if envelope["ok"] as? Bool == true {
            return envelope["value"] as? String ?? ""
        }
        throw CoreError(message: envelope["error"] as? String ?? "Something went wrong.")
    }

    static func initialize(dataDir: String, cacheDir: String) throws {
        _ = try call { spotifast_init(dataDir, cacheDir) }
    }

    static func status() throws -> String { try call { spotifast_status() } }

    static func beginWebLogin(clientId: String) throws -> String {
        try call { spotifast_begin_web_login(clientId) }
    }

    static func beginPlaybackLogin() throws -> String { try call { spotifast_begin_playback_login() } }

    /// Blocks until the sign-in started last finishes.
    static func awaitLogin() throws { _ = try call { spotifast_await_login() } }

    static func cancelLogin() { _ = try? call { spotifast_cancel_login() } }

    static func signOut() throws { _ = try call { spotifast_sign_out() } }

    /// One Web API request; `path` is under /v1 or a full `next` URL.
    static func api(_ method: String, _ path: String, body: String? = nil) throws -> String {
        if let body {
            return try call { body.withCString { spotifast_api(method, path, $0) } }
        }
        return try call { spotifast_api(method, path, nil) }
    }

    /// Makes this phone a Spotify Connect device.
    static func connect(deviceName: String) throws { _ = try call { spotifast_connect(deviceName) } }

    static func disconnect() { _ = try? call { spotifast_disconnect() } }

    /// A player command as JSON, for example `{"cmd":"toggle"}`.
    static func command(_ json: [String: Any]) throws {
        let data = try JSONSerialization.data(withJSONObject: json)
        let text = String(decoding: data, as: UTF8.self)
        _ = try call { spotifast_command(text) }
    }

    static func playerState() throws -> String { try call { spotifast_player_state() } }
}

/// Runs a blocking call off the main thread.
func background<T: Sendable>(_ work: @escaping @Sendable () throws -> T) async throws -> T {
    try await Task.detached(priority: .userInitiated) { try work() }.value
}
