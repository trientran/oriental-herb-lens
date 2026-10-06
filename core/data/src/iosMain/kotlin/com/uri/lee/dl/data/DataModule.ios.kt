package com.uri.lee.dl.data

import com.uri.lee.dl.data.catalog.CatalogSource
import com.uri.lee.dl.data.catalog.IosCatalogSource
import com.uri.lee.dl.data.content.ContentFiles
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

internal actual val platformDataModule: Module = module {
    includes(mobileDataModule)
    single { ContentFiles(applicationSupportDirectory().toPath()) }
    single<CatalogSource> { IosCatalogSource(get(), get()) }
}

/** Not backed up to iCloud by default and never purged, unlike Caches. */
@OptIn(ExperimentalForeignApi::class)
private fun applicationSupportDirectory(): String =
    requireNotNull(
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )?.path,
    ) { "No Application Support directory" }
