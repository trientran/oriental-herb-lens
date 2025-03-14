package com.uri.lee.dl

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class LoginActivity : AppCompatActivity() {

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        Timber.d(auth.currentUser.toString())
        if (auth.currentUser != null) {
            finishAffinity()
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private val signInLauncher = registerForActivityResult(FirebaseAuthUIActivityResultContract()) {
        if (it.resultCode == RESULT_OK) AuthUI.getInstance().auth.currentUser?.let {
            lifecycleScope.launch(mainDispatcher) {
                try {
                    userCollection
                        .document(it.uid)
                        .set(mapOf("uid" to it.uid), SetOptions.merge())
                        .await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e.message)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        authUI.auth.addAuthStateListener(authStateListener)
    }

    override fun onStop() {
        super.onStop()
        authUI.auth.removeAuthStateListener(authStateListener)
    }
}
