package com.uri.lee.dl.shared

import com.uri.lee.dl.core.common.AppInfo
import com.uri.lee.dl.core.common.ApplicationScope
import com.uri.lee.dl.core.ml.NativeEmbedder
import com.uri.lee.dl.core.ml.NativeGeneralLabeler
import com.uri.lee.dl.core.ml.NativeHerbLabeler
import com.uri.lee.dl.core.ml.NativeObjectDetector
import com.uri.lee.dl.domain.notification.UploadNotifier
import com.uri.lee.dl.domain.usecase.SyncContentUseCase
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import com.uri.lee.dl.domain.training.AppFiles
import com.uri.lee.dl.domain.upload.UploadScheduler
import com.uri.lee.dl.shared.training.LocalBackbones
import org.koin.dsl.module

/**
 * Called from Swift at launch, after `FirebaseApp.configure()`, with the ML Kit bridges (ML Kit
 * for iOS has no Kotlin bindings). Also checks for a newer model and catalog, as Android's
 * WorkManager job does.
 */
fun startKoinIos(
    versionName: String,
    versionCode: Long,
    isDebug: Boolean,
    photoUploadUrl: String,
    labeler: NativeHerbLabeler,
    detector: NativeObjectDetector,
    generalLabeler: NativeGeneralLabeler,
    embedder: NativeEmbedder,
) {
    if (isDebug) DebugLog.start()
    val app = AppInfo(versionName, versionCode, platform = "ios", isDebug = isDebug, photoUploadUrl = photoUploadUrl)
    val koin = startKoin {
        modules(sharedModules(app) + module {
            single { labeler }
            single { detector }
            single { generalLabeler }
            single { embedder }
            single<UploadNotifier> { IosUploadNotifier(get()) }
            single<AppFiles> { IosAppFiles() }
            single<UploadScheduler> { InProcessUploadScheduler(get(), get()) }
            if (isDebug) single<LocalBackbones> { IosLocalBackbones() }
        })
    }.koin
    koin.runStartupTasks()
    koin.get<ApplicationScope>().launch { runCatching { koin.get<SyncContentUseCase>()() } }
}
