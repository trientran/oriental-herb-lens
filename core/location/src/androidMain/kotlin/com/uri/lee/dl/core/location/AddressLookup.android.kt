package com.uri.lee.dl.core.location

import android.content.Context
import android.location.Geocoder
import com.uri.lee.dl.core.common.AppDispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.IOException
import java.util.Locale

internal class GeocoderAddressLookup(private val context: Context, private val dispatchers: AppDispatchers) : AddressLookup {

    override suspend fun addressLine(latitude: Double, longitude: Double): String? = withContext(dispatchers.io) {
        try {
            @Suppress("DEPRECATION") // the async overload needs API 33
            Geocoder(context, Locale.getDefault()).getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
                ?.getAddressLine(0)
        } catch (e: IOException) {
            null // offline or the geocoder service is unavailable
        }
    }
}

actual val locationModule: Module = module {
    single<AddressLookup> { GeocoderAddressLookup(androidContext(), get()) }
}
