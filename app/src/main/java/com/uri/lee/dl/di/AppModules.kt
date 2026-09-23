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
import com.uri.lee.dl.domain.repository.SpeciesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
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
}

val appModules = listOf(coreModule, dataModule)
