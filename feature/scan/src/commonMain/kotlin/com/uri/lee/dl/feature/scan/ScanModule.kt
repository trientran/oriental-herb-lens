package com.uri.lee.dl.feature.scan

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val scanModule = module {
    viewModelOf(::ScanViewModel)
}
