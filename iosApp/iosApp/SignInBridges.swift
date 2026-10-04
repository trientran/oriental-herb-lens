import AuthenticationServices
import ComposeApp
import CryptoKit
import FirebaseCore
import GoogleSignIn
import UIKit

/** Google's account chooser; the ID token becomes a Firebase session in shared code. */
final class GoogleSignInProvider: NSObject, GoogleSignInBridge {
    func signInWithGoogle(completion: @escaping (String?, String?) -> Void) {
        guard let clientID = FirebaseApp.app()?.options.clientID else {
            completion(nil, "GoogleService-Info.plist has no CLIENT_ID: enable Google sign-in in Firebase and download it again")
            return
        }
        guard let presenter = UIApplication.shared.topViewController else {
            completion(nil, "Nothing to present the account chooser on")
            return
        }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
            if let error = error as NSError?, error.code == GIDSignInError.canceled.rawValue {
                completion(nil, nil)
            } else if let error {
                completion(nil, error.localizedDescription)
            } else {
                completion(result?.user.idToken?.tokenString, nil)
            }
        }
    }
}

/**
 * Sign in with Apple, required by App Store guideline 4.8 next to Google sign-in. Firebase checks
 * the token against the SHA-256 of a random nonce, so the raw nonce goes back with the token.
 */
final class AppleSignInProvider: NSObject, AppleSignInBridge, ASAuthorizationControllerDelegate,
    ASAuthorizationControllerPresentationContextProviding {
    private var nonce: String?
    private var completion: ((String?, String?, String?) -> Void)?

    func signInWithApple(completion: @escaping (String?, String?, String?) -> Void) {
        let nonce = Self.randomNonce()
        self.nonce = nonce
        self.completion = completion
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [] // contributions only need an account, not a name or email
        request.nonce = SHA256.hash(data: Data(nonce.utf8)).map { String(format: "%02x", $0) }.joined()
        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        controller.performRequests()
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let tokenData = credential.identityToken,
              let token = String(data: tokenData, encoding: .utf8) else {
            finish(nil, nil, "Apple returned no identity token")
            return
        }
        finish(token, nonce, nil)
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        if (error as? ASAuthorizationError)?.code == .canceled {
            finish(nil, nil, nil)
        } else {
            finish(nil, nil, error.localizedDescription)
        }
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        UIApplication.shared.topViewController?.view.window ?? ASPresentationAnchor()
    }

    private func finish(_ token: String?, _ nonce: String?, _ error: String?) {
        completion?(token, nonce, error)
        completion = nil
        self.nonce = nil
    }

    private static func randomNonce(length: Int = 32) -> String {
        let characters = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var generator = SystemRandomNumberGenerator()
        return String((0..<length).map { _ in characters.randomElement(using: &generator)! })
    }
}

extension UIApplication {
    /** The view controller currently on top, to present system sheets from. */
    var topViewController: UIViewController? {
        let window = connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first
        var top = window?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
