package com.uri.lee.dl.domain.usecase

import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.domain.model.GeoLocation
import com.uri.lee.dl.domain.repository.UploadedImage
import com.uri.lee.dl.fakes.FakeAuthRepository
import com.uri.lee.dl.fakes.FakeContributionRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SubmitImagesUseCaseTest {

    private data class Picked(val name: String) : LocalImage

    private val contributions = FakeContributionRepository()
    private val auth = FakeAuthRepository(uid = "uid-1")
    private val hereLocation = GeoLocation(10.0, 106.0)

    private fun useCase(unreadable: Set<String> = emptySet(), hostFailsFor: Set<String> = emptySet()) =
        SubmitImagesUseCase(
            compressor = { image -> (image as Picked).name.takeUnless { it in unreadable }?.toByteArray() },
            host = { jpeg ->
                val name = String(jpeg)
                if (name in hostFailsFor) throw IOException("upload failed")
                "https://img/$name.jpg"
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
            listOf(42L to listOf(UploadedImage("https://img/a.jpg", "uid-1", hereLocation), UploadedImage("https://img/b.jpg", "uid-1", hereLocation))),
            contributions.images,
        )
    }

    @Test
    fun `a failing image is skipped and the rest are still saved`() = runTest {
        val progress = useCase(unreadable = setOf("a"), hostFailsFor = setOf("b"))(
            42, listOf(Picked("a"), Picked("b"), Picked("c")), null,
        ).toList()

        assertEquals(SubmitProgress.Finished(uploaded = 1, failed = 2), progress.last())
        assertEquals(listOf("https://img/c.jpg"), contributions.images.single().second.map { it.url })
    }

    @Test
    fun `nothing is written when every image fails`() = runTest {
        useCase(unreadable = setOf("a"))(42, listOf(Picked("a")), null).toList()

        assertTrue(contributions.images.isEmpty())
    }

    @Test(expected = NotSignedInException::class)
    fun `signed-out users can't contribute`() = runTest {
        auth.userId.value = null
        useCase()(42, listOf(Picked("a")), null).toList()
    }
}
