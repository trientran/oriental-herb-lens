package com.uri.lee.dl.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.uri.lee.dl.core.designsystem.theme.HerbLensTheme
import com.uri.lee.dl.domain.model.PhotoCredit
import com.uri.lee.dl.domain.model.PhotoSource
import com.uri.lee.dl.domain.model.ScanSettings
import com.uri.lee.dl.domain.model.Species
import com.uri.lee.dl.domain.model.SpeciesPhoto
import com.uri.lee.dl.domain.search.NameKind
import com.uri.lee.dl.domain.search.SpeciesMatch
import com.uri.lee.dl.domain.model.GeoLocation
import com.uri.lee.dl.feature.auth.SignInScreen
import com.uri.lee.dl.feature.auth.SignInState
import com.uri.lee.dl.feature.browse.BrowseScreen
import com.uri.lee.dl.feature.contribute.ContributePlatform
import com.uri.lee.dl.feature.contribute.ContributeScreen
import com.uri.lee.dl.feature.contribute.ContributeState
import com.uri.lee.dl.feature.contribute.PickedLocation
import com.uri.lee.dl.feature.contribute.UploadPhase
import com.uri.lee.dl.feature.browse.BrowseState
import com.uri.lee.dl.feature.herbdetails.HerbDetailsScreen
import com.uri.lee.dl.feature.herbdetails.HerbDetailsState
import com.uri.lee.dl.domain.ml.Region
import com.uri.lee.dl.domain.model.RecognizedHerb
import com.uri.lee.dl.feature.scan.BatchItem
import com.uri.lee.dl.feature.scan.ScanMode
import com.uri.lee.dl.feature.scan.ScanScreen
import com.uri.lee.dl.feature.scan.ScanSource
import com.uri.lee.dl.feature.scan.ScanState
import com.uri.lee.dl.feature.scan.ShownObject
import com.uri.lee.dl.feature.profile.ProfileActions
import com.uri.lee.dl.feature.profile.ProfileScreen
import com.uri.lee.dl.feature.profile.ProfileState
import com.uri.lee.dl.feature.saved.SavedScreen
import com.uri.lee.dl.feature.saved.SavedState
import com.uri.lee.dl.feature.saved.SavedTab
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every Compose screen in light and dark on a phone, plus Vietnamese text. A changed screen
 * fails the comparison; record the new look on purpose (see composeApp/build.gradle.kts).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test fun browse() = both("browse") { BrowseScreen(BrowseState(species = Samples.catalog, isLoading = false), {}, {}, selectedId = null) }

    @Test fun search() = both("search") {
        BrowseScreen(BrowseState(species = Samples.catalog, isLoading = false, query = "dinh lang", results = Samples.matches), {}, {}, selectedId = null)
    }

    @Test fun searchNoResults() = light("search_empty") {
        BrowseScreen(BrowseState(isLoading = false, query = "xyz"), {}, {}, selectedId = null)
    }

    @Test fun saved() = both("saved") { SavedScreen(SavedState(favorites = Samples.catalog.take(3), history = emptyList()), {}, {}, null) }

    @Test fun savedEmpty() = light("saved_empty_history") {
        SavedScreen(SavedState(tab = SavedTab.HISTORY, favorites = emptyList(), history = emptyList()), {}, {}, null)
    }

    @Test fun details() = both("details") {
        HerbDetailsScreen(
            HerbDetailsState(herbId = 3035652, species = Samples.dinhLang, referencePhotos = Samples.photos, isFavorite = true),
            onAction = {}, onBack = {}, onAddPhotos = {}, onSuggestName = {},
        )
    }

    @Test fun detailsWithoutPhotos() = light("details_no_photos") {
        HerbDetailsScreen(HerbDetailsState(herbId = 3035652, species = Samples.dinhLang), onAction = {}, onBack = {}, onAddPhotos = {}, onSuggestName = {})
    }

    @Test fun profile() = both("profile") {
        ProfileScreen(ProfileState(isSignedIn = false, scanSettings = ScanSettings(), versionName = "1.1"), {}, ProfileActions(onSignIn = {}, onShareApp = {}, onOpenLanguageSettings = {}))
    }

    @Test fun scanCamera() = both("scan_camera") {
        ScanScreen(
            ScanState(results = listOf(RecognizedHerb("3035652", 0.92f, Samples.dinhLang), RecognizedHerb("2766278", 0.74f, Samples.catalog[3]))),
            onAction = {}, onPickPhotos = {}, onOpenSpecies = {},
        )
    }

    @Test fun scanPickPlant() = light("scan_pick_plant") {
        ScanScreen(
            ScanState(
                mode = ScanMode.PICK_PLANT,
                source = ScanSource.Photo("https://example.org/photo.jpg", aspect = 0.75f),
                objects = listOf(ShownObject(0, Region(0.1f, 0.2f, 0.6f, 0.6f)), ShownObject(1, Region(0.5f, 0.55f, 0.9f, 0.9f))),
                selectedId = 0,
                results = listOf(RecognizedHerb("3035652", 0.88f, Samples.dinhLang)),
            ),
            onAction = {}, onPickPhotos = {}, onOpenSpecies = {},
        )
    }

    @Test fun scanPhotos() = light("scan_photos") {
        ScanScreen(
            ScanState(
                source = ScanSource.Photos(
                    listOf(
                        BatchItem("https://example.org/1.jpg", herbs = listOf(RecognizedHerb("3035652", 0.94f, Samples.dinhLang))),
                        BatchItem("https://example.org/2.jpg", herbs = emptyList()),
                        BatchItem("https://example.org/3.jpg"),
                        BatchItem("https://example.org/4.jpg", failed = true),
                    ),
                ),
            ),
            onAction = {}, onPickPhotos = {}, onOpenSpecies = {},
        )
    }

    @Test fun signIn() = both("sign_in") { SignInScreen(SignInState(), onGoogle = {}, onClose = {}) }

    @Test fun contribute() = both("contribute") {
        ContributeScreen(
            ContributeState(herbId = 3035652, speciesName = "Đinh lăng", location = PickedLocation(GeoLocation(21.03, 105.85), "Hoàn Kiếm, Hà Nội")),
            onAction = {}, platform = ContributePlatform(pickPhotos = {}, currentLocation = {}), onSignIn = {}, onDone = {},
        )
    }

    @Test fun contributeSignedOut() = light("contribute_signed_out") {
        ContributeScreen(ContributeState(herbId = 3035652, speciesName = "Đinh lăng", isSignedIn = false), {}, ContributePlatform(pickPhotos = {}), {}, {})
    }

    @Test fun contributeUploading() = light("contribute_uploading") {
        ContributeScreen(ContributeState(herbId = 3035652, speciesName = "Đinh lăng", phase = UploadPhase.Uploading(2, 5)), {}, ContributePlatform(pickPhotos = {}), {}, {})
    }

    @Config(qualifiers = "+vi")
    @Test fun browseVietnamese() = light("browse_vi") { BrowseScreen(BrowseState(species = Samples.catalog, isLoading = false), {}, {}, selectedId = null) }

    private fun both(name: String, content: @Composable () -> Unit) {
        var dark by mutableStateOf(false)
        show({ dark }, content)
        compose.onRoot().captureRoboImage("$DIR/${name}_light.png")
        dark = true
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$DIR/${name}_dark.png")
    }

    private fun light(name: String, content: @Composable () -> Unit) {
        show({ false }, content)
        compose.onRoot().captureRoboImage("$DIR/$name.png")
    }

    private fun show(dark: () -> Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            // Inspection mode: maps and network images draw placeholders, as in IDE previews
            CompositionLocalProvider(LocalInspectionMode provides true) {
                HerbLensTheme(darkTheme = dark()) {
                    Surface(color = MaterialTheme.colorScheme.background) { Box(Modifier) { content() } }
                }
            }
        }
    }

    private companion object {
        const val DIR = "src/androidHostTest/screenshots"
    }
}

private object Samples {
    val dinhLang = Species(
        id = 3035652,
        scientificName = "Polyscias fruticosa",
        authorship = "(L.) Harms",
        family = "Araliaceae",
        genus = "Polyscias",
        vietnameseNames = listOf("Đinh lăng", "Cây gỏi cá", "Nam dương lâm"),
        englishNames = listOf("Ming aralia"),
    )
    val catalog = listOf(
        dinhLang,
        Species(2927192, "Mentha arvensis", "L.", "Lamiaceae", "Mentha", listOf("Bạc hà"), listOf("Wild mint")),
        Species(3152707, "Abelmoschus esculentus", "(L.) Moench", "Malvaceae", "Abelmoschus", listOf("Mướp tây", "Đậu bắp"), listOf("Okra")),
        Species(2766278, "Cordyline fruticosa", "(L.) A.Chev.", "Asparagaceae", "Cordyline", listOf("Huyết dụ"), listOf("Ti plant")),
        Species(5, "Stachytarpheta jamaicensis", "(L.) Vahl", "Verbenaceae", "Stachytarpheta", listOf("Cỏ roi ngựa"), emptyList()),
        Species(6, "Piper sarmentosum", "Roxb.", "Piperaceae", "Piper", listOf("Lá lốt"), emptyList()),
    )
    val matches = listOf(
        SpeciesMatch(dinhLang, "Đinh lăng", NameKind.VIETNAMESE, 0..8, 1030),
        SpeciesMatch(
            Species(1, "Polyscias guilfoylei", "", "Araliaceae", "Polyscias", listOf("Đinh lăng lá tròn"), emptyList()),
            "Đinh lăng lá tròn", NameKind.VIETNAMESE, 0..8, 830,
        ),
    )
    val photos = listOf(
        SpeciesPhoto(
            url = "https://example.org/original.jpg",
            thumbnailUrl = "https://example.org/medium.jpg",
            source = PhotoSource.GBIF,
            credit = PhotoCredit("Nguyễn Văn A", "CC BY-NC 4.0", null, "iNaturalist", "https://www.gbif.org/occurrence/1"),
        ),
    )
}
