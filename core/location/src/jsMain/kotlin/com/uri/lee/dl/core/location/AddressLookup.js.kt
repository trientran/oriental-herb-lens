package com.uri.lee.dl.core.location

import org.koin.core.module.Module
import org.koin.dsl.module

actual val locationModule: Module = module {
    single<AddressLookup> { AddressLookup { _, _ -> null } }
}
