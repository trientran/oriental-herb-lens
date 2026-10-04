package com.uri.lee.dl.core.location

import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.CoreLocation.CLGeocoder
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLPlacemark
import kotlin.coroutines.resume

internal class ClGeocoderAddressLookup : AddressLookup {

    override suspend fun addressLine(latitude: Double, longitude: Double): String? =
        suspendCancellableCoroutine { continuation ->
            val geocoder = CLGeocoder()
            continuation.invokeOnCancellation { geocoder.cancelGeocode() }
            geocoder.reverseGeocodeLocation(CLLocation(latitude = latitude, longitude = longitude)) { placemarks, _ ->
                val place = placemarks?.firstOrNull() as? CLPlacemark
                val line = place?.let {
                    listOfNotNull(it.name, it.locality, it.administrativeArea, it.country).distinct().joinToString(", ")
                }
                continuation.resume(line?.ifEmpty { null })
            }
        }
}

actual val locationModule: Module = module {
    single<AddressLookup> { ClGeocoderAddressLookup() }
}
