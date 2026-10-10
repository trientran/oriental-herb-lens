package com.uri.lee.dl.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.uri.lee.dl.core.designsystem.LocalSharesPhotos
import com.uri.lee.dl.core.designsystem.theme.HerbLensTheme
import com.uri.lee.dl.domain.analytics.Analytics
import com.uri.lee.dl.domain.analytics.NoAnalytics
import com.uri.lee.dl.domain.location.AddressLine
import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.ml.HerbClassifier
import com.uri.lee.dl.domain.ml.ObjectFinder
import com.uri.lee.dl.domain.ml.ReadPhoto
import com.uri.lee.dl.domain.model.Classification
import com.uri.lee.dl.domain.moderation.ModerationRepository
import com.uri.lee.dl.domain.repository.AuthRepository
import com.uri.lee.dl.domain.repository.ContributionRepository
import com.uri.lee.dl.domain.repository.PhotoRepository
import com.uri.lee.dl.domain.repository.ReferencePhotoRepository
import com.uri.lee.dl.domain.repository.SpeciesRepository
import com.uri.lee.dl.domain.repository.UserLibraryRepository
import com.uri.lee.dl.domain.usecase.IdentifyPlantsUseCase
import com.uri.lee.dl.domain.usecase.RecognizeHerbsUseCase
import com.uri.lee.dl.feature.auth.GoogleCredential
import com.uri.lee.dl.feature.auth.SignInRoute
import com.uri.lee.dl.feature.auth.SignInWindowBlockedException
import com.uri.lee.dl.feature.auth.authModule
import com.uri.lee.dl.feature.herbdetails.HerbDetailsRoute
import com.uri.lee.dl.feature.herbdetails.herbDetailsModule
import com.uri.lee.dl.feature.scan.ScanAction
import com.uri.lee.dl.feature.scan.ScanScreen
import com.uri.lee.dl.feature.scan.ScanViewModel
import com.uri.lee.dl.testing.fakes.FakeAuthRepository
import com.uri.lee.dl.testing.fakes.FakeContributionRepository
import com.uri.lee.dl.testing.fakes.FakeModerationRepository
import com.uri.lee.dl.testing.fakes.FakePhotoRepository
import com.uri.lee.dl.testing.fakes.FakeSettingsRepository
import com.uri.lee.dl.testing.fakes.FakeSpeciesRepository
import com.uri.lee.dl.testing.fakes.FakeUserLibraryRepository
import com.uri.lee.dl.testing.fakes.species
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Taps through the main flows on the real screens and view models, with fake data behind them:
 * what a user would do, and what they should see. No device, camera or network needed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class InteractionTest {

    @get:Rule
    val compose = createComposeRule()

    private val auth = FakeAuthRepository(uid = null)
    private val contributions = FakeContributionRepository()

    private fun koin() = startKoin {
        modules(
            authModule,
            herbDetailsModule,
            module {
                single<AuthRepository> { auth }
                single<ContributionRepository> { contributions }
                single<SpeciesRepository> { FakeSpeciesRepository(listOf(species(HERB, "Polyscias fruticosa", vi = listOf("Đinh lăng")))) }
                single<PhotoRepository> { FakePhotoRepository() }
                single<ReferencePhotoRepository> {
                    object : ReferencePhotoRepository {
                        override suspend fun photos(speciesId: Long, limit: Int) = emptyList<Nothing>()
                    }
                }
                single<UserLibraryRepository> { FakeUserLibraryRepository() }
                single<ModerationRepository> { FakeModerationRepository() }
                single<AddressLine> { AddressLine { null } }
                single<Analytics> { NoAnalytics }
            },
        )
    }

    @After
    fun tearDown() = stopKoin()

    private fun show(sharesPhotos: Boolean = true, content: @Composable () -> Unit) {
        koin()
        compose.setContent {
            HerbLensTheme { CompositionLocalProvider(LocalSharesPhotos provides sharesPhotos, content = content) }
        }
    }

    // Sign-in

    @Test
    fun `signing in with Google closes the sign-in screen`() {
        var closed = false
        show { SignInRoute(requestGoogleSignIn = { GoogleCredential("token") }, onDone = { closed = true }) }

        compose.onNodeWithText("Continue with Google").performClick()

        compose.waitUntil { closed }
        assertEquals("google-token", auth.currentUserId)
    }

    @Test
    fun `a blocked sign-in window says so and the button works again`() {
        show(sharesPhotos = false) { SignInRoute(requestGoogleSignIn = { throw SignInWindowBlockedException() }, onDone = {}) }

        compose.onNodeWithText("Continue with Google").performClick()

        compose.onNodeWithText("Your browser blocked the sign-in window", substring = true).assertExists()
        compose.onNodeWithText("Continue with Google").assertIsEnabled()
    }

    @Test
    fun `an error that isn't an Exception still ends the sign-in`() {
        // As the browser's own errors are on the web: these once left the screen busy for good
        show { SignInRoute(requestGoogleSignIn = { throw Throwable("popup failed") }, onDone = {}) }

        compose.onNodeWithText("Continue with Google").performClick()

        compose.onNodeWithText("Couldn’t sign in", substring = true).assertExists()
        compose.onNodeWithText("Continue with Google").assertIsEnabled()
    }

    // A herb's page

    @Test
    fun `signed out suggesting a name asks to sign in`() {
        var signIn = false
        show { HerbDetailsRoute(HERB, onBack = {}, onAddPhotos = {}, onSignIn = { signIn = true }) }

        compose.onNodeWithText("Suggest a vernacular name").performClick()
        compose.onNodeWithText("Sign in to suggest a name.").assertExists()
        compose.onNodeWithText("Sign in").performClick()

        assertTrue(signIn)
    }

    @Test
    fun `signed in a suggested name is sent with its language`() {
        auth.userId.value = "user-1"
        show { HerbDetailsRoute(HERB, onBack = {}, onAddPhotos = {}, onSignIn = {}) }

        compose.onNodeWithText("Suggest a vernacular name").performClick()
        // The language chip, not the Names card's label
        compose.onAllNodesWithText("Vietnamese").filterToOne(hasClickAction()).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Cây đinh lăng")
        compose.onNodeWithText("Send").performClick()

        compose.onNodeWithText("Thank you! Your suggestion has been sent.").assertExists()
        assertEquals(listOf(Triple(HERB, "vi", "Cây đinh lăng")), contributions.names)

        // Another one: the sheet starts afresh rather than thanking again
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Suggest a vernacular name").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Polyscias")
        compose.onNodeWithText("Send").performClick()
        assertEquals(2, contributions.names.size)
    }

    @Test
    fun `the apps add photos from a herb's page`() {
        var addedTo: Long? = null
        show { HerbDetailsRoute(HERB, onBack = {}, onAddPhotos = { addedTo = it }, onSignIn = {}) }

        compose.onAllNodesWithText("Add photos")[0].performClick()

        assertEquals(HERB, addedTo)
        compose.onAllNodes(hasText("Google Play")).assertCountEquals(0)
    }

    @Test
    fun `the web points to the apps for adding photos`() {
        show(sharesPhotos = false) { HerbDetailsRoute(HERB, onBack = {}, onAddPhotos = null, onSignIn = {}) }

        compose.onAllNodes(hasText("Add photos")).assertCountEquals(0)
        compose.onNodeWithText("use the Med Herb Lens app on Android or iPhone", substring = true).assertExists()
        compose.onNodeWithText("Google Play").assertExists()
        compose.onNodeWithText("App Store").assertExists()
    }

    // Identify

    private data class Photo(override val uri: String) : LocalImage
    private data class Seen(val name: String) : ClassifierImage

    /** Identify on a picked photo: "leaf" is recognised, "blank" isn't, "broken" can't be read. */
    private fun identify(photo: String, onOpen: (Long) -> Unit = {}) {
        val classifier = object : HerbClassifier {
            override suspend fun classify(image: ClassifierImage, minConfidence: Float, maxResults: Int) =
                if ((image as Seen).name == "leaf") listOf(Classification("$HERB", 0.9f)) else emptyList()
        }
        val recognize = RecognizeHerbsUseCase(classifier, FakeSpeciesRepository(listOf(species(HERB, "Polyscias fruticosa", en = listOf("Ming aralia")))))
        val finder = object : ObjectFinder {
            override suspend fun find(image: ClassifierImage, fromCamera: Boolean) = emptyList<Nothing>()
        }
        val viewModel = ScanViewModel(
            recognizeHerbs = recognize,
            identifyPlants = IdentifyPlantsUseCase(finder, recognize),
            objectFinder = finder,
            cropper = { image, _ -> image },
            photoReader = { picked -> if (picked.uri == "broken") null else ReadPhoto(Seen(picked.uri), 400, 300) },
            settings = FakeSettingsRepository(),
        )
        show {
            val state by viewModel.state.collectAsState()
            ScanScreen(
                state = state,
                onAction = viewModel::onAction,
                onPickPhotos = { viewModel.onAction(ScanAction.PhotosPicked(listOf(Photo(photo)))) },
                onOpenSpecies = onOpen,
            )
        }
        // The research-preview note comes first, once
        compose.onNodeWithText("I understand").performClick()
        compose.onNodeWithText("Photos", useUnmergedTree = true).performClick()
    }

    @Test
    fun `a picked photo shows the herb and opens its page`() {
        var opened: Long? = null
        identify("leaf") { opened = it }

        compose.onNodeWithText("Ming aralia").performClick()

        assertEquals(HERB, opened)
    }

    @Test
    fun `a photo with no herb in it says so`() {
        identify("blank")

        compose.onNodeWithText("No herb recognised in this photo.").assertExists()
    }

    @Test
    fun `a photo that can't be read says so`() {
        identify("broken")

        compose.onNodeWithText("This photo couldn’t be read.").assertExists()
    }

    private companion object {
        const val HERB = 5L
    }
}
