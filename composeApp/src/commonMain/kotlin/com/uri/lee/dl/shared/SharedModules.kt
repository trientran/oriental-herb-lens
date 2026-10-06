package com.uri.lee.dl.shared

import com.uri.lee.dl.core.common.AppDispatchers
import com.uri.lee.dl.core.common.AppInfo
import com.uri.lee.dl.core.common.ApplicationScope
import com.uri.lee.dl.core.common.Clock
import com.uri.lee.dl.core.common.text.PlatformTextNormalizer
import com.uri.lee.dl.core.common.text.TextNormalizer
import androidx.compose.ui.text.intl.Locale
import com.uri.lee.dl.domain.model.NamePreference
import com.uri.lee.dl.core.datastore.dataStoreModule
import com.uri.lee.dl.core.firebase.firebaseModule
import com.uri.lee.dl.core.location.locationModule
import com.uri.lee.dl.core.ml.mlModule
import com.uri.lee.dl.core.network.networkModule
import com.uri.lee.dl.data.StartupTasks
import com.uri.lee.dl.data.dataModule
import com.uri.lee.dl.domain.usecase.IdentifyPlantsUseCase
import com.uri.lee.dl.domain.usecase.RecognizeHerbsUseCase
import com.uri.lee.dl.domain.usecase.SubmitImagesUseCase
import com.uri.lee.dl.domain.usecase.SyncContentUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.Koin
import org.koin.core.module.Module
import com.uri.lee.dl.core.location.AddressLookup
import com.uri.lee.dl.feature.auth.authModule
import com.uri.lee.dl.feature.browse.browseModule
import com.uri.lee.dl.feature.contribute.AddressLine
import com.uri.lee.dl.feature.contribute.contributeModule
import com.uri.lee.dl.feature.herbdetails.herbDetailsModule
import com.uri.lee.dl.feature.profile.profileModule
import com.uri.lee.dl.feature.saved.savedModule
import com.uri.lee.dl.feature.scan.scanModule
import com.uri.lee.dl.feature.training.trainingModule
import com.uri.lee.dl.domain.training.Backbones
import com.uri.lee.dl.shared.training.DefaultBackbones
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

private val coreModule = module {
    single<NamePreference> {
        val app = get<AppInfo>()
        NamePreference { vietnameseFirst(app) }
    }
    single { AppDispatchers() }
    single { ApplicationScope(CoroutineScope(SupervisorJob() + get<AppDispatchers>().default)) }
    single { Clock.System }
    single<TextNormalizer> { PlatformTextNormalizer }
}

private val domainModule = module {
    factoryOf(::RecognizeHerbsUseCase)
    factoryOf(::SyncContentUseCase)
    factoryOf(::SubmitImagesUseCase)
    factoryOf(::IdentifyPlantsUseCase)
}

/**
 * Everything the shared app needs, the same on every platform. Each app adds its own modules (screens,
 * platform services) and starts Koin with `sharedModules(appInfo) + its modules`.
 */
fun sharedModules(app: AppInfo): List<Module> = listOf(
    module { single { app } },
    coreModule,
    platformStorageModule,
    dataStoreModule,
    networkModule,
    firebaseModule,
    mlModule,
    locationModule,
    dataModule,
    domainModule,
    module { viewModelOf(::AppViewModel) },
    browseModule,
    savedModule,
    herbDetailsModule,
    profileModule,
    authModule,
    contributeModule,
    scanModule,
    trainingModule,
    module { single { AddressLine { get<AddressLookup>().addressLine(it.latitude, it.longitude) } } },
    // User-trained models: each platform binds AppFiles, and LocalBackbones on developers' builds
    module { single<Backbones> { DefaultBackbones(get(), get(), getOrNull()) } },
)

/** The SQLite database on Android and iOS; nothing in the browser, which keeps the catalog in memory. */
internal expect val platformStorageModule: Module

/**
 * Species are named in Vietnamese first for readers using Vietnamese, with the region set to
 * Vietnam, or (on the web) in Vietnam's time zone; in English first for everyone else.
 */
internal fun vietnameseFirst(app: AppInfo): Boolean =
    Locale.current.language == "vi" || Locale.current.region == "VN" || app.inVietnam

/** Launch-time background work; call once after Koin starts. */
fun Koin.runStartupTasks() = get<StartupTasks>().launchIn(get<ApplicationScope>())
