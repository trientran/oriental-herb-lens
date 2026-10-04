package com.uri.lee.dl.data

import com.uri.lee.dl.data.catalog.AndroidCatalogSource
import com.uri.lee.dl.data.catalog.CatalogSource
import com.uri.lee.dl.data.content.ContentFiles
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformDataModule: Module = module {
    single { ContentFiles(androidContext().filesDir.toOkioPath()) }
    single<CatalogSource> { AndroidCatalogSource(androidContext().assets, get(), get()) }
}
