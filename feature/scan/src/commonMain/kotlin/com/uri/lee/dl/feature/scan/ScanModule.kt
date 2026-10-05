package com.uri.lee.dl.feature.scan

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val scanModule = module {
    // Spelled out: the view model's clock has a default, which viewModelOf would try to inject
    viewModel { ScanViewModel(get(), get(), get(), get(), get(), get()) }
}
