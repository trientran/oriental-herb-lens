import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.initialize
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.w3c.fetch.RequestInit
import kotlin.js.json

// The Firebase web app's config (public by design; access is guarded by rules and App Check)
private val options = FirebaseOptions(
    applicationId = "1:305495327770:web:7e210d3102b0f943ccfda3",
    apiKey = "AIzaSyCoChHx0fzK1GV3GZqA4MGnDObbebSCyBQ",
    projectId = "oriental-herb-lens-41d17",
    storageBucket = "oriental-herb-lens-41d17.appspot.com",
    gcmSenderId = "305495327770",
    authDomain = "oriental-herb-lens-41d17.firebaseapp.com",
)

// Fraud Defense (reCAPTCHA Enterprise) site key for med-herb-lens.pages.dev and localhost
private const val APP_CHECK_SITE_KEY = "6LcUD-EtAAAAAAuRJ26g4Y7RKvYC5P_iCYjlZwcm"
private const val PHOTO_UPLOAD_URL = "https://herb-lens-photo-upload.aunhi55.workers.dev"

// The default app GitLive initialised, from the same JS SDK instance
@JsModule("firebase/app")
@JsNonModule
private external object AppJs {
    fun getApp(): dynamic
}

@JsModule("firebase/app-check")
@JsNonModule
private external object AppCheckJs {
    class ReCaptchaEnterpriseProvider(siteKey: String)
    fun initializeAppCheck(app: dynamic, options: dynamic): dynamic
    fun getToken(appCheck: dynamic, forceRefresh: Boolean): kotlin.js.Promise<dynamic>
}

@JsModule("firebase/auth")
@JsNonModule
private external object AuthJs {
    class GoogleAuthProvider
    fun getAuth(app: dynamic): dynamic
    fun signInWithPopup(auth: dynamic, provider: dynamic): kotlin.js.Promise<dynamic>
}

private val appCheck: dynamic by lazy {
    Firebase.initialize(null, options)
    AppCheckJs.initializeAppCheck(
        AppJs.getApp(),
        json("provider" to AppCheckJs.ReCaptchaEnterpriseProvider(APP_CHECK_SITE_KEY), "isTokenAutoRefreshEnabled" to true),
    )
}

@Composable
actual fun FirebaseChecks() {
    val scope = rememberCoroutineScope()
    var log by remember { mutableStateOf(listOf<String>()) }
    fun say(line: String) { log = log + line }
    fun check(name: String, block: suspend () -> String) = scope.launch {
        say("$name…")
        say(runCatching { block() }.fold({ "$name: $it" }, { "$name failed: ${it.message}" }))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({
                check("App Check") {
                    val token = AppCheckJs.getToken(appCheck, false).await()
                    "token ${(token.token as String).take(12)}…"
                }
            }) { Text("App Check") }
            Button({
                check("Firestore") {
                    appCheck
                    val herb = Firebase.firestore.collection("herbs").document("2705276").get()
                    if (herb.exists) "herbs/2705276 read" else "herbs/2705276 doesn't exist (read allowed)"
                }
            }) { Text("Firestore") }
            Button({
                check("Sign in") {
                    appCheck
                    val result = AuthJs.signInWithPopup(AuthJs.getAuth(AppJs.getApp()), AuthJs.GoogleAuthProvider()).await()
                    "signed in as ${result.user.displayName}"
                }
            }) { Text("Google sign-in") }
            Button({
                check("Worker") {
                    // An invalid species key: the Worker checks the token and the ban, then refuses
                    // with 400 before storing anything
                    val token = Firebase.auth.currentUser?.getIdToken(false) ?: error("sign in first")
                    val response = window.fetch(
                        "$PHOTO_UPLOAD_URL/photos?speciesKey=spike",
                        // Kotlin's RequestInit() sets every option it isn't given to null, which
                        // fetch rejects (cache: null), so pass only what's set
                        json("method" to "POST", "headers" to json("Authorization" to "Bearer $token", "Content-Type" to "image/jpeg"))
                            .unsafeCast<RequestInit>(),
                    ).await()
                    "HTTP ${response.status} ${response.text().await()}"
                }
            }) { Text("Worker") }
        }
        log.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}
