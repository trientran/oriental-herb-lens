package com.uri.lee.dl.feature.contribute

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val contributeModule = module {
    viewModel { (herbId: Long) -> ContributeViewModel(herbId, get(), get(), get(), get(), get()) }
}
