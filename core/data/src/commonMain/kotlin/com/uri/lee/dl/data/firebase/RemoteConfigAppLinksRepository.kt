package com.uri.lee.dl.data.firebase

import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.firebase.RemoteConfigClient
import com.uri.lee.dl.domain.model.AppLinks
import com.uri.lee.dl.domain.repository.AppLinksRepository
import kotlinx.coroutines.CancellationException

/**
 * The Remote Config keys `contact_emails` (addresses separated by commas, the first one first in the email)
 * and `dataset_url` (a web link). Unset, or not valid, means the built-in [AppLinks] value.
 */
internal class RemoteConfigAppLinksRepository(private val remoteConfig: RemoteConfigClient) : AppLinksRepository {

    override suspend fun links(): AppLinks {
        try {
            remoteConfig.fetchAndActivate()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "Remote Config fetch failed; using cached values" }
        }
        return parse(remoteConfig.string(CONTACT_EMAILS), remoteConfig.string(DATASET_URL))
    }

    companion object {
        const val CONTACT_EMAILS = "contact_emails"
        const val DATASET_URL = "dataset_url"
        private val log = Logger.withTag("AppLinks")
        private val email = Regex("[^\\s@,;]+@[^\\s@,;]+\\.[^\\s@,;]+")

        fun parse(contactEmails: String, datasetUrl: String): AppLinks {
            val emails = contactEmails.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            // All or nothing: one bad address would make the mail app reject the rest
            val validEmails = emails.takeIf { it.isNotEmpty() && it.all(email::matches) }
            if (emails.isNotEmpty() && validEmails == null) log.w { "$CONTACT_EMAILS has an address that isn't valid: $contactEmails" }
            // Only web links: the app opens it as it is
            val url = datasetUrl.trim().takeIf { it.startsWith("https://") || it.startsWith("http://") }
            if (datasetUrl.isNotBlank() && url == null) log.w { "$DATASET_URL is not a web link: $datasetUrl" }
            return AppLinks(
                contactEmails = validEmails ?: AppLinks.DEFAULT_CONTACT_EMAILS,
                datasetUrl = url ?: AppLinks.DEFAULT_DATASET_URL,
            )
        }
    }
}
