package com.uri.lee.dl.feature.training

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val trainingModule = module {
    single { UserModelStore(get()) }
    viewModelOf(::TrainingViewModel)
}
