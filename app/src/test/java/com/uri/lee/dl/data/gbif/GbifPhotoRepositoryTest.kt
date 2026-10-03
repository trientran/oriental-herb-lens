package com.uri.lee.dl.data.gbif

import com.uri.lee.dl.core.common.AppDispatchers
import com.uri.lee.dl.data.network.herbLensHttpClient
import com.uri.lee.dl.domain.model.PhotoCredit
import com.uri.lee.dl.domain.model.PhotoSource
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GbifPhotoRepositoryTest {

    private val body = """
        {"results": [
          {"key": 111, "media": [
            {"type": "StillImage", "identifier": "https://inaturalist-open-data.s3.amazonaws.com/photos/1/original.jpg",
             "references": "https://www.inaturalist.org/photos/1", "creator": "alice", "publisher": "iNaturalist",
             "license": "http://creativecommons.org/licenses/by-nc/4.0/", "rightsHolder": "alice"},
            {"type": "StillImage", "identifier": "https://inaturalist-open-data.s3.amazonaws.com/photos/2/large.jpg"},
            {"type": "Sound", "identifier": "https://x/original.jpg"}
          ]},
          {"key": 222, "media": [
            {"type": "StillImage", "identifier": "https://example.org/a/ORIGINAL.JPEG?x=1", "rightsHolder": "bob",
             "license": "CC_BY_4_0"},
            {"type": "StillImage", "identifier": "https://inaturalist-open-data.s3.amazonaws.com/photos/1/original.jpg"}
          ]},
          {"key": 333}
        ]}
    """.trimIndent()

    private var requests = mutableListOf<String>()
    private val engine = MockEngine { request ->
        requests += request.url.toString()
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    private val dispatcher = StandardTestDispatcher()
    private val repository = GbifPhotoRepository(herbLensHttpClient(engine), AppDispatchers(dispatcher, dispatcher, dispatcher))

    @Test
    fun `keeps only original stills, once each, with credits`() = runTest(dispatcher) {
        val photos = repository.photos(3035652)

        assertEquals(
            listOf("https://inaturalist-open-data.s3.amazonaws.com/photos/1/original.jpg", "https://example.org/a/ORIGINAL.JPEG?x=1"),
            photos.map { it.url },
        )
        assertTrue(photos.all { it.source == PhotoSource.GBIF })
        assertEquals(
            PhotoCredit("alice", "CC BY-NC 4.0", "http://creativecommons.org/licenses/by-nc/4.0/", "iNaturalist", "https://www.inaturalist.org/photos/1"),
            photos[0].credit,
        )
        assertEquals("bob", photos[1].credit?.creator)
        assertEquals("CC BY 4.0", photos[1].credit?.license)
        assertEquals("https://www.gbif.org/occurrence/222", photos[1].credit?.sourceUrl)
    }

    @Test
    fun `lists use iNaturalist's medium size, other hosts the original`() = runTest(dispatcher) {
        val photos = repository.photos(1)

        assertEquals("https://inaturalist-open-data.s3.amazonaws.com/photos/1/medium.jpg", photos[0].thumbnailUrl)
        assertEquals(photos[1].url, photos[1].thumbnailUrl)
    }

    @Test
    fun `asks GBIF for still images of the species and caches the answer`() = runTest(dispatcher) {
        repository.photos(3035652)
        repository.photos(3035652, limit = 1)

        assertEquals(1, requests.size)
        assertTrue(requests.single().startsWith("https://api.gbif.org/v1/occurrence/search?"))
        assertTrue("mediaType=StillImage" in requests.single())
        assertTrue("taxon_key=3035652" in requests.single())
    }

    @Test
    fun `licence names`() {
        assertEquals("CC BY-NC 4.0", GbifPhotoRepository.shortLicense("http://creativecommons.org/licenses/by-nc/4.0/"))
        assertEquals("CC BY-SA 4.0", GbifPhotoRepository.shortLicense("https://creativecommons.org/licenses/by-sa/4.0/legalcode"))
        assertEquals("CC0 1.0", GbifPhotoRepository.shortLicense("http://creativecommons.org/publicdomain/zero/1.0/"))
        assertEquals("CC BY-NC 4.0", GbifPhotoRepository.shortLicense("CC_BY_NC_4_0"))
        assertEquals("CC0 1.0", GbifPhotoRepository.shortLicense("CC0_1_0"))
        assertEquals("Some custom licence", GbifPhotoRepository.shortLicense("Some custom licence"))
    }
}
