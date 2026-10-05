package com.uri.lee.dl.core.datastore

import okio.Path
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.koin.androidContext
import org.koin.core.scope.Scope
import java.io.File

internal actual fun Scope.dataStoreDirectory(): Path = File(androidContext().filesDir, "datastore").toOkioPath()
