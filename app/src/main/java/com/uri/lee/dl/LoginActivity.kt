package com.uri.lee.dl

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.google.firebase.auth.FirebaseAuth

@SuppressLint("RestrictedApi")
class LoginActivity : AppCompatActivity() {

    private val auth get() = AuthUI.getInstance().auth

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        if (auth.currentUser != null) {
            finishAffinity()
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    // A successful sign-in is picked up by authStateListener.
    private val signInLauncher = registerForActivityResult(FirebaseAuthUIActivityResultContract()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_login)
        val signInIntent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(listOf(AuthUI.IdpConfig.GoogleBuilder().build()))
            .setLogo(R.drawable.ic_launcher_round)
            .setTosAndPrivacyPolicyUrls(
                if (isSystemLanguageVietnamese) TERMS_OF_SERVICE_VI else TERMS_OF_SERVICE_EN,
                if (isSystemLanguageVietnamese) PRIVACY_POLICY_VI else PRIVACY_POLICY_EN
            )
            .setTheme(R.style.AppTheme)
            .build()
        findViewById<Button>(R.id.sign_in_button).setOnClickListener {
            it.isEnabled = false
            signInLauncher.launch(signInIntent)
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<Button>(R.id.sign_in_button).isEnabled = true
    }

    override fun onStart() {
        super.onStart()
        auth.addAuthStateListener(authStateListener)
    }

    override fun onStop() {
        super.onStop()
        auth.removeAuthStateListener(authStateListener)
    }
}
