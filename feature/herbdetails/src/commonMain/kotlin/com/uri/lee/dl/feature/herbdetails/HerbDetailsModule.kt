package com.uri.lee.dl.feature.herbdetails

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val herbDetailsModule = module {
    viewModel { (herbId: Long) -> HerbDetailsViewModel(herbId, get(), get(), get(), get(), get()) }
    viewModel { (herbId: Long, currentName: String) -> SuggestNameViewModel(herbId, currentName, get(), get()) }
}
