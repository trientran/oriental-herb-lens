package com.uri.lee.dl.shared

import com.uri.lee.dl.core.common.AppInfo
import org.koin.core.context.startKoin

/** Called from Swift at launch (Phase 5), after `FirebaseApp.configure()`. */
fun startKoinIos(versionName: String, versionCode: Long, isDebug: Boolean, photoUploadUrl: String) {
    val koin = startKoin {
        modules(sharedModules(AppInfo(versionName, versionCode, platform = "ios", isDebug, photoUploadUrl)))
    }.koin
    koin.runStartupTasks()
}
