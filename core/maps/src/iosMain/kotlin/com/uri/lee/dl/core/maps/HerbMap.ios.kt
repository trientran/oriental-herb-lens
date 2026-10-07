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
import platform.MapKit.MKCoordinateRegionMake
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKCoordinateSpanMake
import platform.MapKit.MKMapView
import kotlin.math.abs
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKPointAnnotation
import platform.darwin.NSObject

/** MapKit, with the same framing and centre reporting as the Android map. */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun HerbMap(
    points: List<LatLng>,
    modifier: Modifier,
    interactive: Boolean,
    onCenterChanged: ((LatLng) -> Unit)?,
    showMarkers: Boolean,
    onPointClick: ((Int) -> Unit)?,
) {
    val currentOnCenterChanged by rememberUpdatedState(onCenterChanged)
    val currentOnPointClick by rememberUpdatedState(onPointClick)
    // MKMapView holds its delegate weakly, so the composition keeps it
    val delegate = remember { MapDelegate() }
    delegate.onCenterChanged = { currentOnCenterChanged?.invoke(it) }
    delegate.onPointClick = { currentOnPointClick?.invoke(it) }
    delegate.points = points
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
            if (points.size > 1) {
                // Fit them all, with a margin, as Android does
                val minLat = points.minOf { it.latitude }
                val maxLat = points.maxOf { it.latitude }
                val minLon = points.minOf { it.longitude }
                val maxLon = points.maxOf { it.longitude }
                map.setRegion(
                    MKCoordinateRegionMake(
                        CLLocationCoordinate2DMake((minLat + maxLat) / 2, (minLon + maxLon) / 2),
                        MKCoordinateSpanMake(((maxLat - minLat) * 1.3).coerceIn(0.05, 170.0), ((maxLon - minLon) * 1.3).coerceIn(0.05, 360.0)),
                    ),
                    animated = false,
                )
                return@UIKitView
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

@OptIn(ExperimentalForeignApi::class)
private class MapDelegate : NSObject(), MKMapViewDelegateProtocol {
    var onCenterChanged: (LatLng) -> Unit = {}
    var onPointClick: (Int) -> Unit = {}
    var points: List<LatLng> = emptyList()
    var framed = false
    var framedPoints: List<LatLng> = emptyList()

    /** Called once the map comes to rest after a drag, a zoom or framing. */
    @ObjCSignatureOverride
    override fun mapView(mapView: MKMapView, regionDidChangeAnimated: Boolean) {
        mapView.centerCoordinate.useContents { onCenterChanged(LatLng(latitude, longitude)) }
    }

    /** A marker was tapped: which point it is, then unselected so the next tap reports again. */
    @ObjCSignatureOverride
    override fun mapView(mapView: MKMapView, didSelectAnnotationView: MKAnnotationView) {
        val annotation = didSelectAnnotationView.annotation ?: return
        val index = annotation.coordinate.useContents {
            points.indexOfFirst { abs(it.latitude - latitude) < 1e-9 && abs(it.longitude - longitude) < 1e-9 }
        }
        mapView.deselectAnnotation(annotation, animated = false)
        if (index >= 0) onPointClick(index)
    }
}
