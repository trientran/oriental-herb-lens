package com.uri.lee.dl.core.location

import org.koin.core.module.Module

/** Reverse-geocodes a point to a one-line address. */
fun interface AddressLookup {
    /** The address, or null when there is none or the lookup failed. */
    suspend fun addressLine(latitude: Double, longitude: Double): String?
}

/** Binds [AddressLookup]. */
expect val locationModule: Module
