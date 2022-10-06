package com.uri.lee.dl

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class LoginActivity : AppCompatActivity() {
    private val signInLauncher = registerForActivityResult(FirebaseAuthUIActivityResultContract()) {
        if (it.resultCode == RESULT_OK) AuthUI.getInstance().auth.currentUser?.let {
            lifecycleScope.launch(mainDispatcher) {
                try {
                    userCollection
                        .document(it.uid)
                        .set(mapOf("uid" to it.uid), SetOptions.merge())
                        .await()
                    val i = baseContext.packageManager.getLaunchIntentForPackage(baseContext.packageName)
                    i!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(i)
                    finish()
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

    override fun onBackPressed() {
        super.onBackPressed()
        finishAffinity()
    }
}
