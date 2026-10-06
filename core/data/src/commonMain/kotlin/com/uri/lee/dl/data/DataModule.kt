package com.uri.lee.dl.data

import com.uri.lee.dl.core.common.AppInfo
import com.uri.lee.dl.core.datastore.PreferenceStore
import com.uri.lee.dl.data.catalog.SpeciesCsvReader
import com.uri.lee.dl.data.content.ReleaseSource
import com.uri.lee.dl.data.content.RemoteConfigReleaseSource
import com.uri.lee.dl.data.firebase.DefaultAppStatusRepository
import com.uri.lee.dl.data.analytics.FirebaseAnalyticsLogger
import com.uri.lee.dl.data.analytics.UsageStatisticsSync
import com.uri.lee.dl.data.firebase.FirebaseAuthRepository
import com.uri.lee.dl.domain.analytics.Analytics
import com.uri.lee.dl.data.firebase.UserProfileSync
import com.uri.lee.dl.data.moderation.DefaultModerationRepository
import com.uri.lee.dl.domain.moderation.ModerationRepository
import com.uri.lee.dl.data.firebase.FirestoreContributionRepository
import com.uri.lee.dl.data.firebase.FirestorePhotoRepository
import com.uri.lee.dl.data.gbif.GbifPhotoRepository
import com.uri.lee.dl.data.settings.DataStoreSettingsRepository
import com.uri.lee.dl.data.upload.R2PhotoHost
import com.uri.lee.dl.domain.media.ImageHost
import com.uri.lee.dl.domain.repository.AppStatusRepository
import com.uri.lee.dl.domain.repository.AuthRepository
import com.uri.lee.dl.domain.repository.ContributionRepository
import com.uri.lee.dl.domain.repository.PhotoRepository
import com.uri.lee.dl.domain.repository.ReferencePhotoRepository
import com.uri.lee.dl.domain.repository.SettingsRepository
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Binds every domain repository. Needs the source modules (datastore, network, firebase, and the
 * database on Android and iOS) and an [AppInfo] in the graph. The platform part adds the catalog,
 * the user's library and the model: SQLite and app files on Android and iOS, memory and
 * `localStorage` in the browser.
 */
val dataModule: Module = module {
    includes(platformDataModule)

    singleOf(::SpeciesCsvReader)
    single<ReleaseSource> { RemoteConfigReleaseSource(get()) }

    single<SettingsRepository> { DataStoreSettingsRepository(get(named(PreferenceStore.SETTINGS))) }
    single { UserProfileSync(get(), get(), get(named(PreferenceStore.SETTINGS))) }
    single<Analytics> { FirebaseAnalyticsLogger(get()) }
    single { UsageStatisticsSync(get(), get()) }
    single<ModerationRepository> { DefaultModerationRepository(get(), get(), get(named(PreferenceStore.SETTINGS))) }

    singleOf(::FirebaseAuthRepository) bind AuthRepository::class
    singleOf(::FirestorePhotoRepository) bind PhotoRepository::class
    singleOf(::FirestoreContributionRepository) bind ContributionRepository::class
    single<AppStatusRepository> {
        val app = get<AppInfo>()
        DefaultAppStatusRepository(get(), versionCode = app.versionCode, platform = app.platform)
    }
    singleOf(::GbifPhotoRepository) bind ReferencePhotoRepository::class
    single<ImageHost> { R2PhotoHost(get(), get(), workerUrl = get<AppInfo>().photoUploadUrl) }

    singleOf(::StartupTasks)
}

/**
 * Binds SpeciesRepository, UserLibraryRepository, LibraryMigration and HerbModelLocator (and on
 * Android and iOS, ContentRepository).
 */
internal expect val platformDataModule: Module
