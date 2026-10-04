package com.uri.lee.dl.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.uri.lee.dl.data.db.HerbLensDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformDriverModule: Module = module {
    single<SqlDriver> { AndroidSqliteDriver(HerbLensDatabase.Schema, androidContext(), DATABASE_NAME) }
}
