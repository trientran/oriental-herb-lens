package com.uri.lee.dl

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.Locale

@Suppress("DEPRECATION")
suspend fun Activity.getCurrentLocation(): DeviceLocation {
    val locationClient = LocationServices.getFusedLocationProviderClient(this)
    return try {
        val location = locationClient.let {
            it.lastLocation.await() ?: it.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
        }
        val geocoder = Geocoder(this, Locale.getDefault())
        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
        Timber.v("Parsed location addresses: $addresses")
        val address = addresses?.firstOrNull()
        val deviceLocation = DeviceLocation(
            lat = location.latitude,
            long = location.longitude,
            addressLine = address?.getAddressLine(0) ?: "",
        )
        Timber.v("Device location: $deviceLocation")
        deviceLocation
    } catch (e: SecurityException) {
        Timber.e("Permission required to fetch location: $e")
        throw e
    } catch (e: Exception) {
        Timber.e("Failed to fetch location: $e")
        throw e
    }
}

suspend fun Activity.fetchCatchingPermittedLocation(
    onLocationFetched: (DeviceLocation) -> Unit,
    onFailure: (t: Throwable) -> Unit,
) {
    if (isUserCurrentLocationAvailable()) {
        val result = runCatching { getCurrentLocation().let(onLocationFetched) }
        result.onFailure(onFailure)
    }
}

data class DeviceLocation(
    val lat: Double,
    val long: Double,
    val addressLine: String,
)

fun Context.isLocationPermissionGranted(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

fun Activity.openAppSettings() {
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    ).also(::startActivity)
}

fun Activity.openLocationSettings() {
    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    startActivity(intent)
}

fun Context.isLocationServiceEnabled(): Boolean {
    val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
        LocationManager.NETWORK_PROVIDER
    )
}

fun Activity.requestLocationEnabled(
    locationPermissionResultLauncher: ActivityResultLauncher<IntentSenderRequest>,
    coroutineScope: CoroutineScope,
) {
    val request = LocationSettingsRequest
        .Builder()
        .addLocationRequest(LocationRequest.Builder(10000).build())
        .setAlwaysShow(true)
        .build()
    val settingsClient = LocationServices.getSettingsClient(this)

    coroutineScope.launch {
        try {
            val locationSettings = settingsClient.checkLocationSettings(request).await()
            if (locationSettings.locationSettingsStates?.isLocationUsable == false) openLocationSettings()
        } catch (e: ResolvableApiException) {
            try {
                locationPermissionResultLauncher.launch(
                    IntentSenderRequest.Builder(e.resolution.intentSender).build()
                )
            } catch (sendEx: IntentSender.SendIntentException) {
                Timber.e("Unable to open location setting dialog: $sendEx")
                openLocationSettings()
            }
        } catch (e: Exception) {
            Timber.e(e)
            openLocationSettings()
        }
    }
}


fun Context.isUserCurrentLocationAvailable(): Boolean =
    isLocationPermissionGranted() && isLocationServiceEnabled()
