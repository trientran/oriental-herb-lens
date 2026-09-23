package com.uri.lee.dl.di

import com.uri.lee.dl.core.common.AppDispatchers
import com.uri.lee.dl.core.common.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.koin.dsl.module

val coreModule = module {
    single { AppDispatchers() }
    single { ApplicationScope(CoroutineScope(SupervisorJob() + get<AppDispatchers>().default)) }
    single { OkHttpClient() }
}

val appModules = listOf(coreModule)
