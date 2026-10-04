package com.uri.lee.dl.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The app's DataStore files. File names are persisted: renaming one loses what users saved in it.
 * Inject one with `get(named(PreferenceStore.SETTINGS))`.
 */
enum class PreferenceStore(val fileName: String) {
    /** Scan settings and one-time migration flags. The upper-case name dates from the first release. */
    SETTINGS("SETTINGS"),

    /** Which content release (model, catalog) is installed. */
    CONTENT("content"),
}

/** Provides one [DataStore] of [Preferences] per [PreferenceStore], in the platform's app storage. */
val dataStoreModule: Module = module {
    PreferenceStore.entries.forEach { store ->
        single<DataStore<Preferences>>(named(store)) {
            PreferenceDataStoreFactory.createWithPath { dataStoreDirectory() / "${store.fileName}.preferences_pb" }
        }
    }
}

/** Same folder Android's `preferencesDataStore` delegate uses, so files written by older versions are found. */
internal expect fun org.koin.core.scope.Scope.dataStoreDirectory(): Path
