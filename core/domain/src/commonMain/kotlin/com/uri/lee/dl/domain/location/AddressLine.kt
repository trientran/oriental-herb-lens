package com.uri.lee.dl.domain.location

import com.uri.lee.dl.domain.model.GeoLocation

/** Reverse geocoding: a one-line address near [location], or null. Injected so features don't depend on core:location. */
fun interface AddressLine {
    suspend operator fun invoke(location: GeoLocation): String?
}
