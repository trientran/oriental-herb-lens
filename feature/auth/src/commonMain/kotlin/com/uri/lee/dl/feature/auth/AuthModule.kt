package com.uri.lee.dl.feature.auth

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authModule = module {
    viewModelOf(::SignInViewModel)
}
