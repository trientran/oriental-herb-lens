package com.uri.lee.dl.shared

import com.uri.lee.dl.core.database.databaseModule
import org.koin.core.module.Module

internal actual val platformStorageModule: Module = databaseModule
