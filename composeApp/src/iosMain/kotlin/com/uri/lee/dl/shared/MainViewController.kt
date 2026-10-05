package com.uri.lee.dl.shared

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Swift's side of Google sign-in: the ID and access tokens (Firebase on iOS needs both), all null
 * when cancelled, or an error message.
 */
interface GoogleSignInBridge {
    fun signInWithGoogle(completion: (idToken: String?, accessToken: String?, error: String?) -> Unit)
}

/**
 * Swift's side of Sign in with Apple: the identity token, raw nonce and authorization code, all
 * null when cancelled. [revokeToken] tells Apple (through Firebase) the app no longer uses the
 * account, when it's deleted.
 */
interface AppleSignInBridge {
    fun signInWithApple(completion: (idToken: String?, rawNonce: String?, authorizationCode: String?, error: String?) -> Unit)

    fun revokeToken(authorizationCode: String, completion: (error: String?) -> Unit)
}

/** The whole app as a view controller, for SwiftUI's UIViewControllerRepresentable. */
fun MainViewController(google: GoogleSignInBridge, apple: AppleSignInBridge): UIViewController {
    lateinit var controller: UIViewController
    val platform = IosPlatform(google, apple) { controller }
    controller = ComposeUIViewController { App(platform.actions()) }
    return controller
}
