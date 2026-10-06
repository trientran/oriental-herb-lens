import com.uri.lee.dl.shared.WebConfig
import com.uri.lee.dl.shared.WebFirebaseConfig
import com.uri.lee.dl.shared.startWebApp
import kotlinx.browser.window

fun main() {
    val isLocal = window.location.hostname == "localhost"
    startWebApp(
        WebConfig(
            versionName = WebBuildConfig.VERSION_NAME,
            isDebug = isLocal,
            photoUploadUrl = WebBuildConfig.PHOTO_UPLOAD_URL,
            // The Firebase web app "Med Herb Lens (web)": these values are public in every page
            firebase = WebFirebaseConfig(
                apiKey = "AIzaSyCoChHx0fzK1GV3GZqA4MGnDObbebSCyBQ",
                appId = "1:305495327770:web:7e210d3102b0f943ccfda3",
                projectId = "oriental-herb-lens-41d17",
                senderId = "305495327770",
                measurementId = "G-3BQ83PSGP6",
            ),
            // Fraud Defense key for med-herb-lens.pages.dev and localhost
            appCheckSiteKey = "6LcUD-EtAAAAAAuRJ26g4Y7RKvYC5P_iCYjlZwcm",
            siteUrl = "https://med-herb-lens.pages.dev",
        ),
    )
}
