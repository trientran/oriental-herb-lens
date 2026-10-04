package com.uri.lee.dl.data.content

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.uri.lee.dl.domain.model.ContentKind
import com.uri.lee.dl.domain.model.ContentRelease
import kotlinx.coroutines.flow.first

/** Records which release of each kind is installed, in its own DataStore file ("content"). */
class InstalledReleaseStore(private val dataStore: DataStore<Preferences>) {

    suspend fun get(kind: ContentKind): ContentRelease? {
        val prefs = dataStore.data.first()
        val url = prefs[urlKey(kind)] ?: return null
        return ContentRelease(url = url, sha256 = prefs[sha256Key(kind)].orEmpty())
    }

    suspend fun set(kind: ContentKind, release: ContentRelease?) {
        dataStore.edit { prefs ->
            if (release == null) {
                prefs.remove(urlKey(kind))
                prefs.remove(sha256Key(kind))
            } else {
                prefs[urlKey(kind)] = release.url
                prefs[sha256Key(kind)] = release.sha256
            }
        }
    }

    private fun urlKey(kind: ContentKind) = stringPreferencesKey("installed_${kind.name.lowercase()}_url")
    private fun sha256Key(kind: ContentKind) = stringPreferencesKey("installed_${kind.name.lowercase()}_sha256")
}
