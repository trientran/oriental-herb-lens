package com.uri.lee.dl.data

import com.uri.lee.dl.core.datastore.PreferenceStore
import com.uri.lee.dl.core.ml.HerbModelLocator
import com.uri.lee.dl.data.catalog.SqlSpeciesRepository
import com.uri.lee.dl.data.content.ContentDownloader
import com.uri.lee.dl.data.content.ContentModelLocator
import com.uri.lee.dl.data.content.DefaultContentRepository
import com.uri.lee.dl.data.content.InstalledReleaseStore
import com.uri.lee.dl.data.library.LegacyLibraryMigration
import com.uri.lee.dl.data.library.LibraryMigration
import com.uri.lee.dl.data.library.LocalUserLibraryRepository
import com.uri.lee.dl.domain.repository.ContentRepository
import com.uri.lee.dl.domain.repository.SpeciesRepository
import com.uri.lee.dl.domain.repository.UserLibraryRepository
import okio.FileSystem
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Android and iOS: the catalog and the user's library in SQLite, and content sync into app files.
 * The platform adds `ContentFiles` (where downloads go) and the `CatalogSource` for the bundled copy.
 */
internal val mobileDataModule: Module = module {
    single<FileSystem> { FileSystem.SYSTEM }

    singleOf(::SqlSpeciesRepository) bind SpeciesRepository::class
    single<HerbModelLocator> { ContentModelLocator(get(), get()) }

    single { InstalledReleaseStore(get(named(PreferenceStore.CONTENT))) }
    singleOf(::ContentDownloader)
    singleOf(::DefaultContentRepository) bind ContentRepository::class

    singleOf(::LocalUserLibraryRepository) bind UserLibraryRepository::class
    single<LibraryMigration> { LegacyLibraryMigration(get(), get(), get(named(PreferenceStore.SETTINGS))) }
}
