package com.uri.lee.dl.core.ml

import com.uri.lee.dl.domain.ml.HerbClassifier
import org.koin.core.module.Module
import org.koin.dsl.module

actual val mlModule: Module = module {
    single<HerbClassifier> { MlKitHerbClassifier(get()) }
}
