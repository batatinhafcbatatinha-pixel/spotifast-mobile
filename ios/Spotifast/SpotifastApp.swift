import SwiftUI

@main
struct SpotifastApp: App {
    @StateObject private var app: AppModel

    init() {
        // Covers stay in memory only, so the app's storage never grows.
        URLCache.shared = URLCache(memoryCapacity: 24 * 1024 * 1024, diskCapacity: 0)
        // The streaming buffers and caches older runs left behind.
        _app = StateObject(wrappedValue: AppModel())
    }

    var body: some Scene {
        WindowGroup {
            RootView().environmentObject(app)
        }
    }
}
