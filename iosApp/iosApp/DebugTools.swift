#if DEBUG
import ComposeApp
import FirebaseAppCheck
import FirebaseCore
import UIKit

/**
 * Debug builds only: ways to see what the app is doing on a device without Xcode attached.
 * Everything lands in Documents/debug, next to the shared log (app.log) and the latest camera
 * frame (frame.jpg); copy it off with tools/ios_debug.sh.
 *
 * - The App Check debug token goes into the log, to add in Firebase > App Check.
 * - Posting the Darwin notification `com.uri.lee.dl.debug.screenshot` (devicectl can) saves what
 *   the screen shows to screen.png.
 */
enum DebugTools {
    static let screenshotNotification = "com.uri.lee.dl.debug.screenshot"

    static func start() {
        logAppCheckToken()
        CFNotificationCenterAddObserver(
            CFNotificationCenterGetDarwinNotifyCenter(),
            nil,
            { _, _, _, _, _ in DispatchQueue.main.async { DebugTools.saveScreenshot() } },
            screenshotNotification as CFString,
            nil,
            .deliverImmediately
        )
    }

    private static func logAppCheckToken() {
        guard let app = FirebaseApp.app(), let provider = AppCheckDebugProvider(app: app) else { return }
        DebugLogKt.debugLog(tag: "AppCheck", message: "Debug token: \(provider.localDebugToken())")
    }

    private static func saveScreenshot() {
        guard let window = UIApplication.shared.connectedScenes
            .compactMap({ ($0 as? UIWindowScene)?.keyWindow }).first else { return }
        let image = UIGraphicsImageRenderer(bounds: window.bounds).image { _ in
            window.drawHierarchy(in: window.bounds, afterScreenUpdates: false)
        }
        let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("debug")
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try? image.pngData()?.write(to: directory.appendingPathComponent("screen.png"))
        DebugLogKt.debugLog(tag: "Debug", message: "Saved screen.png")
    }
}
#endif
