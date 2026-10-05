package com.uri.lee.dl.core.maps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKPointAnnotation
import platform.darwin.NSObject

/** MapKit, with the same framing and centre reporting as the Android map. */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun HerbMap(points: List<LatLng>, modifier: Modifier, interactive: Boolean, onCenterChanged: ((LatLng) -> Unit)?, showMarkers: Boolean) {
    val currentOnCenterChanged by rememberUpdatedState(onCenterChanged)
    // MKMapView holds its delegate weakly, so the composition keeps it
    val delegate = remember { MapDelegate() }
    delegate.onCenterChanged = { currentOnCenterChanged?.invoke(it) }
    UIKitView(
        factory = {
            MKMapView().apply {
                setScrollEnabled(interactive)
                setZoomEnabled(interactive)
                setRotateEnabled(false)
                setPitchEnabled(false)
                setDelegate(delegate)
            }
        },
        modifier = modifier,
        update = { map ->
            map.removeAnnotations(map.annotations)
            if (showMarkers) points.forEach { point ->
                map.addAnnotation(MKPointAnnotation().apply { setCoordinate(CLLocationCoordinate2DMake(point.latitude, point.longitude)) })
            }
            // A map the user moves is framed once; a preview follows its points
            val frame = !delegate.framed || (!interactive && delegate.framedPoints != points)
            delegate.framed = true
            delegate.framedPoints = points
            if (!frame) return@UIKitView
            val center = points.firstOrNull() ?: DefaultCenter
            val metres = if (points.isEmpty()) 1_500_000.0 else 20_000.0
            map.setRegion(
                MKCoordinateRegionMakeWithDistance(CLLocationCoordinate2DMake(center.latitude, center.longitude), metres, metres),
                animated = false,
            )
        },
    )
}

@OptIn(ExperimentalForeignApi::class)
private class MapDelegate : NSObject(), MKMapViewDelegateProtocol {
    var onCenterChanged: (LatLng) -> Unit = {}
    var framed = false
    var framedPoints: List<LatLng> = emptyList()

    /** Called once the map comes to rest after a drag, a zoom or framing. */
    @ObjCSignatureOverride
    override fun mapView(mapView: MKMapView, regionDidChangeAnimated: Boolean) {
        mapView.centerCoordinate.useContents { onCenterChanged(LatLng(latitude, longitude)) }
    }
}
