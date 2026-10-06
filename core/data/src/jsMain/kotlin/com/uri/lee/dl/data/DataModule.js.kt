package com.uri.lee.dl.data

import com.uri.lee.dl.core.datastore.PreferenceStore
import com.uri.lee.dl.core.ml.HerbModelFile
import com.uri.lee.dl.core.ml.HerbModelLocator
import com.uri.lee.dl.data.catalog.MemorySpeciesRepository
import com.uri.lee.dl.data.library.LibraryMigration
import com.uri.lee.dl.data.library.WebUserLibraryRepository
import com.uri.lee.dl.domain.repository.SpeciesRepository
import com.uri.lee.dl.domain.repository.UserLibraryRepository
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** The browser: the catalog in memory, the library in `localStorage`, the model served with the site. */
internal actual val platformDataModule: Module = module {
    single<SpeciesRepository> { MemorySpeciesRepository(get(), get(), get()) }
    single<UserLibraryRepository> { WebUserLibraryRepository(get(named(PreferenceStore.SETTINGS))) }
    // Earlier versions never ran in a browser: nothing to copy
    single { LibraryMigration {} }
    single { HerbModelLocator { HerbModelFile.Bundled("herb_model.tflite") } }
}
