package com.uri.lee.dl.feature.herbdetails

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val herbDetailsModule = module {
    viewModel { (herbId: Long) -> HerbDetailsViewModel(herbId, get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { (herbId: Long, listed: Map<String, List<String>>, language: String) -> SuggestNameViewModel(herbId, listed, language, get(), get(), get()) }
}
