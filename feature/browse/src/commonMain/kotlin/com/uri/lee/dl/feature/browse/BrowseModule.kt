package com.uri.lee.dl.feature.browse

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val browseModule = module {
    viewModel { (sortByVietnameseName: Boolean) -> BrowseViewModel(get(), sortByVietnameseName) }
}
