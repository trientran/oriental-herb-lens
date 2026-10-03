package com.uri.lee.dl.data.upload

import com.uri.lee.dl.data.network.herbLensHttpClient
import com.uri.lee.dl.domain.usecase.NotSignedInException
import com.uri.lee.dl.fakes.FakeAuthRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class R2PhotoHostTest {

    private var lastRequest: HttpRequestData? = null
    private var lastBody: ByteArray? = null
    private var status = HttpStatusCode.Created
    private val engine = MockEngine { request ->
        lastRequest = request
        lastBody = request.body.toByteArray()
        respond(
            """{"url":"https://pub.r2.dev/photos/3035652/abc.jpg"}""",
            status,
            headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
    private val auth = FakeAuthRepository(uid = "u1")
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1)

    private fun host(url: String = "https://worker.example/") = R2PhotoHost(herbLensHttpClient(engine), auth, url)

    @Test
    fun `posts the JPEG with the user's token and returns the stored URL`() = runTest {
        val url = host().upload(3035652, jpeg)

        assertEquals("https://pub.r2.dev/photos/3035652/abc.jpg", url)
        val request = lastRequest!!
        assertEquals("https://worker.example/photos?speciesKey=3035652", request.url.toString())
        assertEquals("Bearer token-for-u1", request.headers[HttpHeaders.Authorization])
        assertEquals("image/jpeg", request.body.contentType.toString())
        assertArrayEquals(jpeg, lastBody)
    }

    @Test(expected = IOException::class)
    fun `a rejected upload is an IOException`() = runTest {
        status = HttpStatusCode.Unauthorized
        host().upload(1, jpeg)
    }

    @Test(expected = NotSignedInException::class)
    fun `signed-out users can't upload`() = runTest {
        auth.userId.value = null
        host().upload(1, jpeg)
    }

    @Test(expected = IOException::class)
    fun `a build without the Worker URL fails clearly`() = runTest {
        host(url = "").upload(1, jpeg)
    }
}
