package com.uri.lee.dl.data.firebase

import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.firebase.RemoteConfigClient
import com.uri.lee.dl.domain.model.LanguageSwitchStore
import kotlinx.coroutines.CancellationException

/**
 * The Remote Config key `all_languages`: `false` shows the interface in English and Vietnamese only, as
 * before the machine-translated languages; unset or anything else, every language. Saved for the next
 * launch, and only after a successful fetch, so being offline keeps the last value seen.
 */
internal class LanguageSwitchSync(private val remoteConfig: RemoteConfigClient, private val store: LanguageSwitchStore) {

    suspend fun run() {
        try {
            remoteConfig.fetchAndActivate()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "Remote Config fetch failed; keeping the saved language switch" }
            return
        }
        store.save(isOn(remoteConfig.string(KEY)))
    }

    companion object {
        const val KEY = "all_languages"
        private val log = Logger.withTag("Languages")

        fun isOn(value: String): Boolean = value.trim().lowercase() != "false"
    }
}
