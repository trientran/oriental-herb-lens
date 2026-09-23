package com.uri.lee.dl.di

import com.uri.lee.dl.core.common.AppDispatchers
import com.uri.lee.dl.core.common.ApplicationScope
import com.uri.lee.dl.core.common.text.TextNormalizer
import com.uri.lee.dl.data.catalog.AndroidCatalogSource
import com.uri.lee.dl.data.catalog.CatalogSource
import com.uri.lee.dl.data.catalog.CsvSpeciesRepository
import com.uri.lee.dl.data.catalog.SpeciesCsvReader
import com.uri.lee.dl.data.content.ContentFiles
import com.uri.lee.dl.data.platform.JvmTextNormalizer
import com.uri.lee.dl.data.ml.HerbModelLocator
import com.uri.lee.dl.data.ml.MlKitHerbClassifier
import com.uri.lee.dl.data.platform.BitmapLoader
import com.uri.lee.dl.data.settings.DataStoreSettingsRepository
import com.uri.lee.dl.dataStore
import com.uri.lee.dl.domain.ml.HerbClassifier
import com.uri.lee.dl.domain.repository.SettingsRepository
import com.uri.lee.dl.domain.repository.SpeciesRepository
import com.uri.lee.dl.domain.usecase.RecognizeHerbsUseCase
import com.uri.lee.dl.lenscamera.CameraViewModel
import com.uri.lee.dl.lenscamera.livecamera.LiveCameraViewModel
import com.uri.lee.dl.lenscamera.objectivecamera.ObjectiveCameraViewModel
import com.uri.lee.dl.lensimage.ImageViewModel
import com.uri.lee.dl.lensimages.ImagesViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val coreModule = module {
    single { AppDispatchers() }
    single { ApplicationScope(CoroutineScope(SupervisorJob() + get<AppDispatchers>().default)) }
    single { OkHttpClient() }
}

val dataModule = module {
    single<TextNormalizer> { JvmTextNormalizer }
    single { ContentFiles(androidContext().filesDir) }
    single<CatalogSource> { AndroidCatalogSource(androidContext().assets, get()) }
    singleOf(::SpeciesCsvReader)
    singleOf(::CsvSpeciesRepository) bind SpeciesRepository::class
    single { androidContext().dataStore }
    singleOf(::DataStoreSettingsRepository) bind SettingsRepository::class
    singleOf(::HerbModelLocator)
    singleOf(::MlKitHerbClassifier) bind HerbClassifier::class
    single { BitmapLoader(androidContext()) }
}

val domainModule = module {
    factoryOf(::RecognizeHerbsUseCase)
}

val viewModelModule = module {
    viewModelOf(::CameraViewModel)
    viewModelOf(::LiveCameraViewModel)
    viewModelOf(::ImageViewModel)
    viewModelOf(::ImagesViewModel)
    viewModel { ObjectiveCameraViewModel(androidApplication(), get()) }
}

val appModules = listOf(coreModule, dataModule, domainModule, viewModelModule)
