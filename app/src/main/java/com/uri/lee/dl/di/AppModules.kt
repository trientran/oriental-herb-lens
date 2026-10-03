package com.uri.lee.dl.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.uri.lee.dl.BuildConfig
import com.uri.lee.dl.MainViewModel
import com.uri.lee.dl.R
import com.uri.lee.dl.core.common.AppDispatchers
import com.uri.lee.dl.core.common.ApplicationScope
import com.uri.lee.dl.core.common.text.TextNormalizer
import com.uri.lee.dl.data.catalog.AndroidCatalogSource
import com.uri.lee.dl.data.catalog.CatalogSource
import com.uri.lee.dl.data.catalog.SpeciesCsvReader
import com.uri.lee.dl.data.catalog.SqlSpeciesRepository
import com.uri.lee.dl.data.content.ContentDownloader
import com.uri.lee.dl.data.content.ContentFiles
import com.uri.lee.dl.data.content.ContentSyncWorker
import com.uri.lee.dl.data.content.DefaultContentRepository
import com.uri.lee.dl.data.content.InstalledReleaseStore
import com.uri.lee.dl.data.content.LegacyModelCleanup
import com.uri.lee.dl.data.content.ReleaseSource
import com.uri.lee.dl.data.content.RemoteConfigReleaseSource
import com.uri.lee.dl.data.db.HerbLensDatabase
import com.uri.lee.dl.data.firebase.DefaultAppStatusRepository
import com.uri.lee.dl.data.firebase.FirebaseAuthRepository
import com.uri.lee.dl.data.firebase.FirestoreContributionRepository
import com.uri.lee.dl.data.firebase.FirestorePhotoRepository
import com.uri.lee.dl.data.firebase.FirestoreUserLibraryRepository
import com.uri.lee.dl.data.ml.HerbModelLocator
import com.uri.lee.dl.data.ml.MlKitHerbClassifier
import com.uri.lee.dl.data.network.herbLensHttpClient
import com.uri.lee.dl.data.platform.AddressLookup
import com.uri.lee.dl.data.platform.AndroidImageCompressor
import com.uri.lee.dl.data.platform.BitmapLoader
import com.uri.lee.dl.data.platform.JvmTextNormalizer
import com.uri.lee.dl.data.settings.DataStoreSettingsRepository
import com.uri.lee.dl.data.upload.ImGeImageHost
import com.uri.lee.dl.dataStore
import com.uri.lee.dl.domain.media.ImageCompressor
import com.uri.lee.dl.domain.media.ImageHost
import com.uri.lee.dl.domain.ml.HerbClassifier
import com.uri.lee.dl.domain.repository.AppStatusRepository
import com.uri.lee.dl.domain.repository.AuthRepository
import com.uri.lee.dl.domain.repository.ContentRepository
import com.uri.lee.dl.domain.repository.ContributionRepository
import com.uri.lee.dl.domain.repository.PhotoRepository
import com.uri.lee.dl.domain.repository.SettingsRepository
import com.uri.lee.dl.domain.repository.SpeciesRepository
import com.uri.lee.dl.domain.repository.UserLibraryRepository
import com.uri.lee.dl.domain.usecase.RecognizeHerbsUseCase
import com.uri.lee.dl.domain.usecase.SubmitImagesUseCase
import com.uri.lee.dl.domain.usecase.SyncContentUseCase
import com.uri.lee.dl.herbdetails.HerbDetailsViewModel
import com.uri.lee.dl.herbdetails.SuggestNameViewModel
import com.uri.lee.dl.hometabs.HomeViewModel
import com.uri.lee.dl.isSystemLanguageVietnamese
import com.uri.lee.dl.lenscamera.CameraViewModel
import com.uri.lee.dl.lenscamera.livecamera.LiveCameraViewModel
import com.uri.lee.dl.lenscamera.objectivecamera.ObjectiveCameraViewModel
import com.uri.lee.dl.lensimage.ImageViewModel
import com.uri.lee.dl.lensimages.ImagesViewModel
import com.uri.lee.dl.search.SearchViewModel
import com.uri.lee.dl.upload.ImageApi
import com.uri.lee.dl.upload.ImageUploadViewModel
import com.uri.lee.dl.upload.RetrofitHelper
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val coreModule = module {
    single { AppDispatchers() }
    single { ApplicationScope(CoroutineScope(SupervisorJob() + get<AppDispatchers>().default)) }
}

/** Third-party SDK objects (Firebase, HTTP clients), kept apart so the graph check treats them as provided. */
val sdkModule = module {
    single { OkHttpClient() }
    single { herbLensHttpClient(OkHttp.create()) }
    single<FirebaseRemoteConfig> {
        Firebase.remoteConfig.apply {
            setConfigSettingsAsync(
                remoteConfigSettings {
                    // Debug builds see newly published releases immediately
                    minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else 3600
                }
            )
            setDefaultsAsync(R.xml.remote_config_defaults)
        }
    }
    single<FirebaseFirestore> { Firebase.firestore }
    single<FirebaseAuth> { Firebase.auth }
}

val dataModule = module {
    single<TextNormalizer> { JvmTextNormalizer }
    single { ContentFiles(androidContext().filesDir) }
    single<CatalogSource> { AndroidCatalogSource(androidContext().assets, get()) }
    singleOf(::SpeciesCsvReader)
    single { HerbLensDatabase(AndroidSqliteDriver(HerbLensDatabase.Schema, androidContext(), "herblens.db")) }
    singleOf(::SqlSpeciesRepository) bind SpeciesRepository::class
    single { androidContext().dataStore }
    singleOf(::DataStoreSettingsRepository) bind SettingsRepository::class
    singleOf(::HerbModelLocator)
    singleOf(::MlKitHerbClassifier) bind HerbClassifier::class
    single { BitmapLoader(androidContext()) }

    single<ReleaseSource> { RemoteConfigReleaseSource(get()) }

    singleOf(::FirebaseAuthRepository) bind AuthRepository::class
    singleOf(::FirestorePhotoRepository) bind PhotoRepository::class
    singleOf(::FirestoreUserLibraryRepository) bind UserLibraryRepository::class
    singleOf(::FirestoreContributionRepository) bind ContributionRepository::class
    single<AppStatusRepository> {
        DefaultAppStatusRepository(get(), get(), get(), versionCode = BuildConfig.VERSION_CODE.toLong())
    }
    single<ImageHost> { ImGeImageHost(RetrofitHelper.getInstance().create(ImageApi::class.java)) }
    single<ImageCompressor> { AndroidImageCompressor(androidContext()) }
    single { AddressLookup(androidContext(), get()) }
    single(named(CONTENT_DATASTORE)) { androidContext().contentDataStore }
    single { InstalledReleaseStore(get(named(CONTENT_DATASTORE))) }
    singleOf(::ContentDownloader)
    singleOf(::DefaultContentRepository) bind ContentRepository::class
    single { LegacyModelCleanup(androidContext()) }
    workerOf(::ContentSyncWorker)
}

private const val CONTENT_DATASTORE = "content"
private val Context.contentDataStore: DataStore<Preferences> by preferencesDataStore(name = CONTENT_DATASTORE)

val domainModule = module {
    factoryOf(::RecognizeHerbsUseCase)
    factoryOf(::SyncContentUseCase)
    factoryOf(::SubmitImagesUseCase)
}

val viewModelModule = module {
    viewModelOf(::CameraViewModel)
    viewModelOf(::LiveCameraViewModel)
    viewModelOf(::ImageViewModel)
    viewModelOf(::ImagesViewModel)
    viewModel { ObjectiveCameraViewModel(androidApplication(), get()) }
    viewModelOf(::MainViewModel)
    viewModel { HomeViewModel(get(), get(), sortByVietnameseName = isSystemLanguageVietnamese) }
    viewModelOf(::HerbDetailsViewModel)
    viewModelOf(::SuggestNameViewModel)
    viewModelOf(::ImageUploadViewModel)
    viewModelOf(::SearchViewModel)
}

val appModules = listOf(coreModule, sdkModule, dataModule, domainModule, viewModelModule)
