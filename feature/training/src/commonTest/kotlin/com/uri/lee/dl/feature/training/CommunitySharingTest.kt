package com.uri.lee.dl.feature.training

import com.uri.lee.dl.domain.ml.ImageEmbedderLoader
import com.uri.lee.dl.domain.sharing.CommunityModel
import com.uri.lee.dl.domain.sharing.HuggingFaceStatus
import com.uri.lee.dl.domain.sharing.ModelReportReason
import com.uri.lee.dl.domain.sharing.SharingProblem
import com.uri.lee.dl.domain.training.AppFiles
import com.uri.lee.dl.domain.training.Backbones
import com.uri.lee.dl.core.common.ApplicationScope
import com.uri.lee.dl.domain.notification.UploadNotifier
import com.uri.lee.dl.testing.MainDispatcherTest
import kotlinx.coroutines.CoroutineScope
import com.uri.lee.dl.testing.fakes.FakeAuthRepository
import com.uri.lee.dl.testing.fakes.FakeCommunityModelRepository
import com.uri.lee.dl.testing.fakes.FakeSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CommunitySharingTest : MainDispatcherTest() {

    private class MemoryFiles : AppFiles {
        val files = mutableMapOf<String, ByteArray>()
        override suspend fun read(name: String) = files[name]
        override suspend fun write(name: String, bytes: ByteArray) { files[name] = bytes }
        override suspend fun append(name: String, text: String) { files[name] = (files[name] ?: ByteArray(0)) + text.encodeToByteArray() }
        override suspend fun delete(name: String) { files.keys.removeAll { it == name || it.startsWith("$name/") } }
        override suspend fun list(folder: String) = files.keys.filter { it.startsWith("$folder/") }.map { it.removePrefix("$folder/").substringBefore('/') }.distinct()
        override suspend fun location(name: String) = name
    }

    private val store = UserModelStore(MemoryFiles())
    private val auth = FakeAuthRepository()
    private val community = FakeCommunityModelRepository { auth.currentUserId }
    private val settings = FakeSettingsRepository()
    private val finished = mutableListOf<Pair<String, Boolean>>()
    private val notifier = object : UploadNotifier {
        override fun uploadFinished(speciesId: Long, speciesName: String?, uploaded: Int, failed: Int) = Unit
        override fun modelShareFinished(modelName: String, shared: Boolean) { finished += modelName to shared }
    }
    private val backbones = object : Backbones {
        override val available = listOf("mobilenet_v3_large")
        override suspend fun location(name: String, onDownloading: () -> Unit) = name
        override suspend fun read(name: String) = ByteArray(0)
    }

    private fun viewModel() = TrainingViewModel(
        store, backbones, reader = { null }, embedders = ImageEmbedderLoader { error("no embedder in tests") },
        cropper = { image, _ -> image }, community = community, auth = auth, settings = settings,
        appScope = ApplicationScope(CoroutineScope(testDispatcher)), notifier = notifier,
    )

    /** A model with its file, open on its results screen. */
    private suspend fun opened(name: String = "Garden herbs"): TrainingViewModel {
        val model = UserModel("m1", name, listOf("Mint", "Basil"), trainedClasses = listOf("Mint", "Basil"))
        store.saveImported(model, ByteArray(100))
        return viewModel().apply { onAction(TrainingAction.Open(TrainingScreen.Result("m1"))) }
    }

    @Test
    fun `sharing asks a signed-out user to sign in`() = runTest {
        auth.userId.value = null
        val viewModel = opened()

        viewModel.onAction(TrainingAction.ShareWithEveryone)

        assertEquals(SharingStep.SignIn, viewModel.state.value.sharing)
        assertTrue(community.shared.value.isEmpty())
    }

    @Test
    fun `the terms come first and once and then the model is shared`() = runTest {
        val viewModel = opened()

        viewModel.onAction(TrainingAction.ShareWithEveryone)
        assertEquals(SharingStep.Terms, viewModel.state.value.sharing)

        viewModel.onAction(TrainingAction.AcceptSharingTerms)
        assertEquals(SharingStep.Confirm, viewModel.state.value.sharing)
        assertTrue(settings.sharingTermsAccepted.value)

        viewModel.onAction(TrainingAction.ConfirmShare(offerToHuggingFace = true))

        assertNull(viewModel.state.value.sharing)
        // The fake doesn't publish, as when Hugging Face is down: shared here, published later
        assertEquals(TrainingMessage.SHARED_HUGGING_FACE_LATER, viewModel.state.value.message)
        val shared = community.shared.value.single()
        assertEquals(listOf("Mint", "Basil"), shared.species)
        assertEquals("user-1", shared.uploaderId)
        assertEquals(HuggingFaceStatus.REQUESTED, shared.huggingFace)
        assertEquals(listOf("Garden herbs" to true), finished)
        // Remembered, so the results screen stops asking
        assertEquals(shared.id, viewModel.state.value.model?.sharedId)
        assertEquals(shared.id, store.load("m1")?.sharedId)
    }

    @Test
    fun `a second share skips the terms`() = runTest {
        settings.sharingTermsAccepted.value = true
        val viewModel = opened()

        viewModel.onAction(TrainingAction.ShareWithEveryone)
        assertEquals(SharingStep.Confirm, viewModel.state.value.sharing)
        viewModel.onAction(TrainingAction.ConfirmShare(offerToHuggingFace = false))

        assertEquals(HuggingFaceStatus.NONE, community.shared.value.single().huggingFace)
    }

    @Test
    fun `contact details in a name stop the share`() = runTest {
        settings.sharingTermsAccepted.value = true
        val viewModel = opened(name = "Ask me@example.com")

        viewModel.onAction(TrainingAction.ShareWithEveryone)

        assertEquals(SharingStep.Problem(SharingProblem.CONTACT_DETAILS), viewModel.state.value.sharing)
        assertTrue(community.shared.value.isEmpty())
    }

    @Test
    fun `others' models can be reported or their sharer hidden and your own removed`() = runTest {
        community.shared.value = listOf(
            CommunityModel("a", "Weeds", listOf("Lantana", "Mimosa"), "mobilenet_v3_large", true, "https://r2/a", 10, "someone"),
            CommunityModel("b", "Weeds 2", listOf("Lantana", "Mimosa"), "mobilenet_v3_large", true, "https://r2/b", 10, "someone"),
            CommunityModel("c", "Mine", listOf("Mint", "Basil"), "mobilenet_v3_large", true, "https://r2/c", 10, "user-1"),
        )
        val viewModel = viewModel()
        viewModel.onAction(TrainingAction.Open(TrainingScreen.Community))
        assertEquals(3, viewModel.state.value.community?.size)

        viewModel.onAction(TrainingAction.Open(TrainingScreen.CommunityModel("a")))
        viewModel.onAction(TrainingAction.ReportCommunityModel("a", ModelReportReason.OFFENSIVE))
        assertEquals(listOf("a" to ModelReportReason.OFFENSIVE), community.reports)
        assertEquals(TrainingScreen.Community, viewModel.state.value.screen)
        assertEquals(listOf("b", "c"), viewModel.state.value.community?.map { it.id })

        viewModel.onAction(TrainingAction.Open(TrainingScreen.CommunityModel("b")))
        viewModel.onAction(TrainingAction.HideSharer("someone"))
        assertEquals(listOf("c"), viewModel.state.value.community?.map { it.id })

        viewModel.onAction(TrainingAction.Open(TrainingScreen.CommunityModel("c")))
        viewModel.onAction(TrainingAction.RemoveCommunityModel("c"))
        assertEquals(TrainingMessage.REMOVED, viewModel.state.value.message)
        assertTrue(community.shared.value.none { it.id == "c" })
    }

    @Test
    fun `the shared list is only listened to while it's open`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(TrainingAction.Open(TrainingScreen.Community))
        assertEquals(emptyList(), viewModel.state.value.community)

        viewModel.onAction(TrainingAction.Back)

        assertNull(viewModel.state.value.community)
    }

    @Test
    fun `a model in the list can be shared without opening it`() = runTest {
        settings.sharingTermsAccepted.value = true
        store.saveImported(UserModel("m2", "Weeds", listOf("Lantana", "Mimosa"), trainedClasses = listOf("Lantana", "Mimosa")), ByteArray(10))
        val viewModel = viewModel()

        viewModel.onAction(TrainingAction.ShareModel("m2"))
        viewModel.onAction(TrainingAction.ConfirmShare(offerToHuggingFace = false))

        assertEquals("Weeds", community.shared.value.single().name)
        assertEquals(community.shared.value.single().id, store.load("m2")?.sharedId)
    }

    @Test
    fun `a failed share says so and the notifier hears of it`() = runTest {
        settings.sharingTermsAccepted.value = true
        community.failUpload = true
        val viewModel = opened()

        viewModel.onAction(TrainingAction.ShareWithEveryone)
        viewModel.onAction(TrainingAction.ConfirmShare(offerToHuggingFace = false))

        assertEquals(TrainingError.SHARE, viewModel.state.value.error)
        assertNull(viewModel.state.value.work)
        assertEquals(listOf("Garden herbs" to false), finished)
    }
}
