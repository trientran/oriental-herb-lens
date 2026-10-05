package com.uri.lee.dl.domain.usecase

import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.domain.model.GeoLocation
import com.uri.lee.dl.domain.repository.UploadedImage
import com.uri.lee.dl.testing.fakes.FakeAuthRepository
import com.uri.lee.dl.testing.fakes.FakeContributionRepository
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class SubmitImagesUseCaseTest {

    private data class Picked(val name: String) : LocalImage {
        override val uri = "test://$name"
    }

    private val contributions = FakeContributionRepository()
    private val auth = FakeAuthRepository(uid = "uid-1")
    private val hereLocation = GeoLocation(10.0, 106.0)

    private fun useCase(unreadable: Set<String> = emptySet(), hostFailsFor: Set<String> = emptySet()) =
        SubmitImagesUseCase(
            compressor = { image -> (image as Picked).name.takeUnless { it in unreadable }?.encodeToByteArray() },
            host = { speciesId, jpeg ->
                val name = jpeg.decodeToString()
                if (name in hostFailsFor) throw IllegalStateException("upload failed")
                "https://img/$speciesId/$name.jpg"
            },
            contributions = contributions,
            auth = auth,
        )

    @Test
    fun `every image is uploaded and attached to the herb in one write`() = runTest {
        val progress = useCase()(42, listOf(Picked("a"), Picked("b")), hereLocation).toList()

        assertEquals(
            listOf(
                SubmitProgress.Uploading(0, 2), SubmitProgress.Uploading(1, 2), SubmitProgress.Uploading(2, 2),
                SubmitProgress.Finished(uploaded = 2, failed = 0),
            ),
            progress,
        )
        assertEquals(
            listOf(42L to listOf(UploadedImage("https://img/42/a.jpg", "uid-1", hereLocation), UploadedImage("https://img/42/b.jpg", "uid-1", hereLocation))),
            contributions.images,
        )
    }

    @Test
    fun `a failing image is skipped and the rest are still saved`() = runTest {
        val progress = useCase(unreadable = setOf("a"), hostFailsFor = setOf("b"))(
            42, listOf(Picked("a"), Picked("b"), Picked("c")), hereLocation,
        ).toList()

        assertEquals(SubmitProgress.Finished(uploaded = 1, failed = 2), progress.last())
        assertEquals(listOf("https://img/42/c.jpg"), contributions.images.single().second.map { it.url })
    }

    @Test
    fun `nothing is written when every image fails`() = runTest {
        useCase(unreadable = setOf("a"))(42, listOf(Picked("a")), hereLocation).toList()

        assertTrue(contributions.images.isEmpty())
    }

    @Test
    fun `signed-out users can't contribute`() = runTest {
        auth.userId.value = null
        assertFailsWith<NotSignedInException> { useCase()(42, listOf(Picked("a")), hereLocation).toList() }
    }
}
