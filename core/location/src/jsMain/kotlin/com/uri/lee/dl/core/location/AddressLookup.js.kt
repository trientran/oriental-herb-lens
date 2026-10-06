package com.uri.lee.dl.core.location

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.js.Promise

/**
 * OpenStreetMap's Nominatim, the browser has no geocoder of its own. Its usage policy allows one
 * request a second with the site identified (the browser sends the Referer): the app looks up a
 * place only when one is chosen.
 */
internal class NominatimAddressLookup : AddressLookup {

    override suspend fun addressLine(latitude: Double, longitude: Double): String? = runCatching {
        val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=16" +
            "&lat=$latitude&lon=$longitude&accept-language=${window.navigator.language},vi,en"
        val response = window.fetch(url).await()
        if (!response.ok) return null
        val place: dynamic = response.json().unsafeCast<Promise<dynamic>>().await()
        val address = place.address ?: return null
        listOf(
            address.road, address.suburb ?: address.village ?: address.town,
            address.city ?: address.county, address.state, address.country,
        ).mapNotNull { it as? String }.distinct().joinToString(", ").ifEmpty { null }
    }.getOrNull()
}

actual val locationModule: Module = module {
    single<AddressLookup> { NominatimAddressLookup() }
}
