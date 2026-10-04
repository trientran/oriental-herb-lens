package com.uri.lee.dl.core.maps

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKPointAnnotation

/** MapKit. Choosing a point by tapping comes with the iOS app (Phase 5); [onTap] is ignored for now. */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun HerbMap(points: List<LatLng>, modifier: Modifier, onTap: ((LatLng) -> Unit)?) {
    UIKitView(
        factory = { MKMapView() },
        modifier = modifier,
        update = { map ->
            map.removeAnnotations(map.annotations)
            points.forEach { point ->
                map.addAnnotation(MKPointAnnotation().apply { setCoordinate(CLLocationCoordinate2DMake(point.latitude, point.longitude)) })
            }
            val center = points.firstOrNull() ?: DefaultCenter
            val metres = if (points.isEmpty()) 1_500_000.0 else 20_000.0
            map.setRegion(
                MKCoordinateRegionMakeWithDistance(CLLocationCoordinate2DMake(center.latitude, center.longitude), metres, metres),
                animated = false,
            )
        },
    )
}
