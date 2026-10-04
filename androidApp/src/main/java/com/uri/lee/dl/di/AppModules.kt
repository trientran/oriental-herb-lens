package com.uri.lee.dl.di

import com.uri.lee.dl.BuildConfig
import com.uri.lee.dl.core.common.AppInfo
import com.uri.lee.dl.data.content.ContentSyncWorker
import com.uri.lee.dl.data.content.LegacyModelCleanup
import com.uri.lee.dl.data.platform.AndroidImageCompressor
import com.uri.lee.dl.data.platform.BitmapLoader
import com.uri.lee.dl.domain.media.ImageCompressor
import com.uri.lee.dl.lenscamera.CameraViewModel
import com.uri.lee.dl.lenscamera.livecamera.LiveCameraViewModel
import com.uri.lee.dl.lenscamera.objectivecamera.ObjectiveCameraViewModel
import com.uri.lee.dl.lensimage.ImageViewModel
import com.uri.lee.dl.lensimages.ImagesViewModel
import com.uri.lee.dl.shared.sharedModules
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appInfo = AppInfo(
    versionName = BuildConfig.VERSION_NAME,
    versionCode = BuildConfig.VERSION_CODE.toLong(),
    platform = "android",
    isDebug = BuildConfig.DEBUG,
    photoUploadUrl = BuildConfig.PHOTO_UPLOAD_URL,
)

/** Android-only services: image decoding, background sync, legacy cleanup, Glide's HTTP client. */
val androidModule = module {
    single { OkHttpClient() }
    single<ImageCompressor> { AndroidImageCompressor(androidContext()) }
    single { BitmapLoader(androidContext()) }
    single { LegacyModelCleanup(androidContext()) }
    workerOf(::ContentSyncWorker)
}

val viewModelModule = module {
    viewModelOf(::CameraViewModel)
    viewModelOf(::LiveCameraViewModel)
    viewModelOf(::ImageViewModel)
    viewModelOf(::ImagesViewModel)
    viewModel { ObjectiveCameraViewModel(androidApplication(), get()) }
}

val appModules = sharedModules(appInfo) + listOf(androidModule, viewModelModule)
