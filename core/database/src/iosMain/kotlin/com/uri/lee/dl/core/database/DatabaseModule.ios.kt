package com.uri.lee.dl.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.uri.lee.dl.data.db.HerbLensDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformDriverModule: Module = module {
    single<SqlDriver> { NativeSqliteDriver(HerbLensDatabase.Schema, DATABASE_NAME) }
}
