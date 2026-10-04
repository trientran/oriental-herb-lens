package com.uri.lee.dl.core.database

import app.cash.sqldelight.db.SqlDriver
import com.uri.lee.dl.data.db.HerbLensDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

internal const val DATABASE_NAME = "herblens.db"

/** Provides [HerbLensDatabase] on the platform's SQLite driver. */
val databaseModule: Module = module {
    includes(platformDriverModule)
    single { HerbLensDatabase(get<SqlDriver>()) }
}

internal expect val platformDriverModule: Module
