package com.uri.lee.dl.shared

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Swift's side of Google sign-in: an ID token, or (null, null) when cancelled, or an error message. */
interface GoogleSignInBridge {
    fun signInWithGoogle(completion: (idToken: String?, error: String?) -> Unit)
}

/** Swift's side of Sign in with Apple: the identity token and raw nonce, (null, null, null) when cancelled. */
interface AppleSignInBridge {
    fun signInWithApple(completion: (idToken: String?, rawNonce: String?, error: String?) -> Unit)
}

/** The whole app as a view controller, for SwiftUI's UIViewControllerRepresentable. */
fun MainViewController(google: GoogleSignInBridge, apple: AppleSignInBridge): UIViewController {
    lateinit var controller: UIViewController
    val platform = IosPlatform(google, apple) { controller }
    controller = ComposeUIViewController { App(platform.actions()) }
    return controller
}
