import ComposeApp
import FirebaseAppCheck
import FirebaseCore
import GoogleSignIn
import SwiftUI
import UserNotifications

/** Firebase and App Check first, then the shared Kotlin app (Koin) with the ML Kit bridges. */
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        #if DEBUG
        // Prints a debug token to the Xcode console; add it in Firebase > App Check > Manage debug tokens
        AppCheck.setAppCheckProviderFactory(AppCheckDebugProviderFactory())
        #else
        AppCheck.setAppCheckProviderFactory(AppAttestProviderFactory())
        #endif
        FirebaseApp.configure()
        // Must be registered before launch ends: lets a research run carry on in the background (iOS 26+)
        IosResearchBackgroundKt.registerResearchBackgroundTask()

        let info = Bundle.main.infoDictionary ?? [:]
        #if DEBUG
        let isDebug = true
        #else
        let isDebug = false
        #endif
        KoinIosKt.startKoinIos(
            versionName: info["CFBundleShortVersionString"] as? String ?? "",
            versionCode: Int64(info["CFBundleVersion"] as? String ?? "") ?? 0,
            isDebug: isDebug,
            photoUploadUrl: info["HerbLensPhotoUploadUrl"] as? String ?? "",
            labeler: MLKitHerbLabeler(),
            detector: MLKitObjectDetector(),
            generalLabeler: MLKitGeneralLabeler(),
            embedder: LiteRTEmbedder()
        )
        #if DEBUG
        DebugTools.start()
        #endif
        // Set before launch ends, so a tap that launched the app is delivered too
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    /** Tapping an upload notification opens the species it's about. */
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if let herbId = response.notification.request.content.userInfo["herbId"] as? NSNumber {
            IosDeepLinks.shared.openHerb(id: herbId.int64Value)
        }
        completionHandler()
    }
}

final class AppAttestProviderFactory: NSObject, AppCheckProviderFactory {
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        AppAttestProvider(app: app)
    }
}

@main
struct HerbLensApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
                // Google sign-in returns through the app's URL scheme
                .onOpenURL { GIDSignIn.sharedInstance.handle($0) }
        }
    }
}

/** The whole app is Compose; SwiftUI only hosts it. */
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(google: GoogleSignInProvider(), apple: AppleSignInProvider())
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
