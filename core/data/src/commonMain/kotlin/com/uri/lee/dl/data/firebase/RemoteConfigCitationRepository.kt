package com.uri.lee.dl.data.firebase

import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.firebase.RemoteConfigClient
import com.uri.lee.dl.domain.model.Citation
import com.uri.lee.dl.domain.repository.CitationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The Remote Config key `how_to_cite`: a JSON list of works, newest first, such as
 * `[{"text": "Tran, T. (2027). Training on the edge… https://doi.org/…", "url": "https://doi.org/…"}]`.
 * Unset, or not valid, means nothing to cite yet.
 */
internal class RemoteConfigCitationRepository(private val remoteConfig: RemoteConfigClient) : CitationRepository {

    override suspend fun citations(): List<Citation> {
        try {
            remoteConfig.fetchAndActivate()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "Remote Config fetch failed; using cached values" }
        }
        return parse(remoteConfig.string(KEY))
    }

    @Serializable
    private class Entry(val text: String = "", val url: String? = null)

    companion object {
        const val KEY = "how_to_cite"
        private val log = Logger.withTag("Citations")
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(value: String): List<Citation> {
            if (value.isBlank()) return emptyList()
            val entries = try {
                json.decodeFromString<List<Entry>>(value)
            } catch (e: Exception) {
                log.w(e) { "$KEY is not a valid list of citations" }
                return emptyList()
            }
            return entries.mapNotNull { entry ->
                val text = entry.text.trim().ifEmpty { return@mapNotNull null }
                // Only web links: the app opens them as they are
                val url = entry.url?.trim()?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                Citation(text, url)
            }
        }
    }
}
