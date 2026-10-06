package com.uri.lee.dl.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.Path
import org.koin.core.scope.Scope

/** A [KeyValueStore] over a Preferences DataStore file; keys keep their names and types. */
class DataStoreKeyValueStore(private val dataStore: DataStore<Preferences>) : KeyValueStore {
    override val data: Flow<StoreValues> = dataStore.data.map { prefs ->
        object : StoreValues {
            override fun <T : Any> get(key: StoreKey<T>): T? = prefs[key.toPreferencesKey()]
        }
    }

    override suspend fun edit(transform: (MutableStoreValues) -> Unit) {
        dataStore.edit { prefs -> transform(MutableValues(prefs)) }
    }

    private class MutableValues(private val prefs: MutablePreferences) : MutableStoreValues {
        override fun <T : Any> get(key: StoreKey<T>): T? = prefs[key.toPreferencesKey()]
        override fun <T : Any> set(key: StoreKey<T>, value: T) { prefs[key.toPreferencesKey()] = value }
        override fun remove(key: StoreKey<*>) { prefs.remove(key.toPreferencesKey()) }
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T : Any> StoreKey<T>.toPreferencesKey(): Preferences.Key<T> = when (type) {
    StoreKey.Type.BOOLEAN -> booleanPreferencesKey(name)
    StoreKey.Type.FLOAT -> floatPreferencesKey(name)
    StoreKey.Type.STRING -> stringPreferencesKey(name)
    StoreKey.Type.STRING_SET -> stringSetPreferencesKey(name)
} as Preferences.Key<T>

internal actual fun Scope.createKeyValueStore(store: PreferenceStore): KeyValueStore = DataStoreKeyValueStore(
    PreferenceDataStoreFactory.createWithPath { dataStoreDirectory() / "${store.fileName}.preferences_pb" },
)

/** Same folder Android's `preferencesDataStore` delegate uses, so files written by older versions are found. */
internal expect fun Scope.dataStoreDirectory(): Path
