package com.uri.lee.dl.di

import com.uri.lee.dl.AndroidAppFiles
import com.uri.lee.dl.AndroidLocalBackbones
import com.uri.lee.dl.AndroidUploadNotifier
import com.uri.lee.dl.BuildConfig
import com.uri.lee.dl.core.common.AppInfo
import com.uri.lee.dl.data.content.ContentSyncWorker
import com.uri.lee.dl.data.content.LegacyCleanup
import com.uri.lee.dl.domain.notification.UploadNotifier
import com.uri.lee.dl.domain.training.AppFiles
import com.uri.lee.dl.shared.sharedModules
import com.uri.lee.dl.shared.training.LocalBackbones
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.dsl.module

val appInfo = AppInfo(
    versionName = BuildConfig.VERSION_NAME,
    versionCode = BuildConfig.VERSION_CODE.toLong(),
    platform = "android",
    isDebug = BuildConfig.DEBUG,
    photoUploadUrl = BuildConfig.PHOTO_UPLOAD_URL,
)

/** Android-only services: background content sync, clean-up after older versions, notifications. */
val androidModule = module {
    single { LegacyCleanup(androidContext()) }
    single<UploadNotifier> { AndroidUploadNotifier(androidContext(), get()) }
    workerOf(::ContentSyncWorker)
    single<AppFiles> { AndroidAppFiles(File(androidContext().filesDir, "app")) }
    if (BuildConfig.DEBUG) single<LocalBackbones> { AndroidLocalBackbones(androidContext()) }
}

val appModules = sharedModules(appInfo) + androidModule
