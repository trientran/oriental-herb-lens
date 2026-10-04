package com.uri.lee.dl.feature.saved

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val savedModule = module {
    viewModelOf(::SavedViewModel)
}
