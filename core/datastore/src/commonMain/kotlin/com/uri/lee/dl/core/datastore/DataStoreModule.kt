package com.uri.lee.dl.core.datastore

import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The app's preference files. File names are persisted: renaming one loses what users saved in it.
 * Inject one with `get(named(PreferenceStore.SETTINGS))`.
 */
enum class PreferenceStore(val fileName: String) {
    /** Scan settings and one-time migration flags. The upper-case name dates from the first release. */
    SETTINGS("SETTINGS"),

    /** Which content release (model, catalog) is installed. */
    CONTENT("content"),
}

/** Provides one [KeyValueStore] per [PreferenceStore], in the platform's app storage. */
val dataStoreModule: Module = module {
    PreferenceStore.entries.forEach { store ->
        single<KeyValueStore>(named(store)) { createKeyValueStore(store) }
    }
}

internal expect fun org.koin.core.scope.Scope.createKeyValueStore(store: PreferenceStore): KeyValueStore
