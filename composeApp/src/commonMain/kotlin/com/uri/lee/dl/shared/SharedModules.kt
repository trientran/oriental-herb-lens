package com.uri.lee.dl.shared

import com.uri.lee.dl.core.common.AppDispatchers
import com.uri.lee.dl.core.common.AppInfo
import com.uri.lee.dl.core.common.ApplicationScope
import com.uri.lee.dl.core.common.Clock
import com.uri.lee.dl.core.common.text.PlatformTextNormalizer
import com.uri.lee.dl.core.common.text.TextNormalizer
import com.uri.lee.dl.core.database.databaseModule
import com.uri.lee.dl.core.datastore.dataStoreModule
import com.uri.lee.dl.core.firebase.firebaseModule
import com.uri.lee.dl.core.location.locationModule
import com.uri.lee.dl.core.ml.mlModule
import com.uri.lee.dl.core.network.networkModule
import com.uri.lee.dl.data.StartupTasks
import com.uri.lee.dl.data.dataModule
import com.uri.lee.dl.domain.usecase.RecognizeHerbsUseCase
import com.uri.lee.dl.domain.usecase.SubmitImagesUseCase
import com.uri.lee.dl.domain.usecase.SyncContentUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.Koin
import org.koin.core.module.Module
import com.uri.lee.dl.feature.browse.browseModule
import com.uri.lee.dl.feature.herbdetails.herbDetailsModule
import com.uri.lee.dl.feature.profile.profileModule
import com.uri.lee.dl.feature.saved.savedModule
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

private val coreModule = module {
    single { AppDispatchers() }
    single { ApplicationScope(CoroutineScope(SupervisorJob() + get<AppDispatchers>().default)) }
    single { Clock.System }
    single<TextNormalizer> { PlatformTextNormalizer }
}

private val domainModule = module {
    factoryOf(::RecognizeHerbsUseCase)
    factoryOf(::SyncContentUseCase)
    factoryOf(::SubmitImagesUseCase)
}

/**
 * Everything the shared app needs, the same on every platform. Each app adds its own modules (screens,
 * platform services) and starts Koin with `sharedModules(appInfo) + its modules`.
 */
fun sharedModules(app: AppInfo): List<Module> = listOf(
    module { single { app } },
    coreModule,
    databaseModule,
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
)

/** Launch-time background work; call once after Koin starts. */
fun Koin.runStartupTasks() = get<StartupTasks>().launchIn(get<ApplicationScope>())
