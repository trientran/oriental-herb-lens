package com.uri.lee.dl.data.platform

import android.content.Context
import android.location.Geocoder
import com.uri.lee.dl.core.common.AppDispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.util.Locale

/** Reverse-geocodes a point to a one-line address. */
class AddressLookup(private val context: Context, private val dispatchers: AppDispatchers) {

    suspend fun addressLine(latitude: Double, longitude: Double): String? = withContext(dispatchers.io) {
        try {
            @Suppress("DEPRECATION") // the async overload needs API 33
            Geocoder(context, Locale.getDefault()).getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
                ?.getAddressLine(0)
        } catch (e: IOException) {
            Timber.w(e, "Reverse geocoding failed")
            null
        }
    }
}
