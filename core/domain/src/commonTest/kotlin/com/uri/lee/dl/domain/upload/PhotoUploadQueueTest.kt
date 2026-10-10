package com.uri.lee.dl.domain.upload

import com.uri.lee.dl.domain.analytics.NoAnalytics
import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.domain.model.GeoLocation
import com.uri.lee.dl.domain.notification.UploadNotifier
import com.uri.lee.dl.domain.repository.UploadedImage
import com.uri.lee.dl.testing.fakes.FakeAuthRepository
import com.uri.lee.dl.testing.fakes.FakeContributionRepository
import com.uri.lee.dl.testing.fakes.MemoryAppFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PhotoUploadQueueTest {

    private data class Picked(val name: String) : LocalImage {
        override val uri = "test://$name"
    }

    private val files = MemoryAppFiles()
    private val contributions = FakeContributionRepository()
    private val auth = FakeAuthRepository(uid = "uid-1")
    private val here = GeoLocation(10.0, 106.0)
    private val sent = mutableListOf<String>()
    private val finished = mutableListOf<Triple<Long, Int, Int>>()
    private var offline = false
    private var failFor = emptySet<String>()

    private fun queue(unreadable: Set<String> = emptySet()) = PhotoUploadQueue(
        files = files,
        compressor = { image -> (image as Picked).name.takeUnless { it in unreadable }?.encodeToByteArray() },
        host = { speciesId, jpeg ->
            val name = jpeg.decodeToString()
            if (offline || name in failFor) throw IllegalStateException("no connection")
            sent += name
            "https://img/$speciesId/$name.jpg"
        },
        contributions = contributions,
        auth = auth,
        notifier = object : UploadNotifier {
            override fun uploadFinished(speciesId: Long, speciesName: String?, uploaded: Int, failed: Int) {
                finished += Triple(speciesId, uploaded, failed)
            }
        },
        analytics = NoAnalytics,
    )

    @Test
    fun `saved photos upload and are attached to the herb in one write`() = runTest {
        val queue = queue()
        val job = queue.enqueue(42, "Mint", listOf(Picked("a"), Picked("b")), here)
        assertTrue(sent.isEmpty())

        queue.runPending()

        assertEquals(
            listOf(42L to listOf(UploadedImage("https://img/42/a.jpg", "uid-1", here), UploadedImage("https://img/42/b.jpg", "uid-1", here))),
            contributions.images,
        )
        assertEquals(SubmitProgress.Finished(uploaded = 2, failed = 0), queue.progress.value[job])
        assertEquals(listOf(Triple(42L, 2, 0)), finished)
        assertTrue(files.files.isEmpty())
    }

    @Test
    fun `a job survives a failed run and picks up where it stopped without sending a photo twice`() = runTest {
        val first = queue()
        first.enqueue(42, "Mint", listOf(Picked("a"), Picked("b")), here)
        failFor = setOf("b")
        first.runPending()
        assertEquals(listOf("a"), sent)
        assertTrue(contributions.images.isEmpty())

        // As after the app was closed: a new queue over the same files
        failFor = emptySet()
        queue().runPending()

        assertEquals(listOf("a", "b"), sent)
        assertEquals(listOf("https://img/42/a.jpg", "https://img/42/b.jpg"), contributions.images.single().second.map { it.url })
        assertTrue(files.files.isEmpty())
    }

    @Test
    fun `after the last attempt what was uploaded is attached and the rest counted as failed`() = runTest {
        val queue = queue(unreadable = setOf("x"))
        queue.enqueue(42, "Mint", listOf(Picked("a"), Picked("b"), Picked("x")), here)
        failFor = setOf("b")

        repeat(PhotoUploadQueue.MAX_ATTEMPTS) { queue.runPending() }

        assertEquals(listOf("https://img/42/a.jpg"), contributions.images.single().second.map { it.url })
        assertEquals(listOf(Triple(42L, 1, 2)), finished)
        assertTrue(files.files.isEmpty())
    }

    @Test
    fun `nothing is written when no photo can be read`() = runTest {
        val queue = queue(unreadable = setOf("a"))
        queue.enqueue(42, "Mint", listOf(Picked("a")), here)

        queue.runPending()

        assertTrue(contributions.images.isEmpty())
        assertEquals(listOf(Triple(42L, 0, 1)), finished)
    }

    @Test
    fun `signed-out users can't contribute`() = runTest {
        auth.userId.value = null
        assertFailsWith<NotSignedInException> { queue().enqueue(42, "Mint", listOf(Picked("a")), here) }
    }
}
