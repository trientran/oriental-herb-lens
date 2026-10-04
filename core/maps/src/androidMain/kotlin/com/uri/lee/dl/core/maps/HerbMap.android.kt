package com.uri.lee.dl.core.maps

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import com.mapbox.maps.plugin.gestures.addOnMapClickListener

@Composable
actual fun HerbMap(points: List<LatLng>, modifier: Modifier, onTap: ((LatLng) -> Unit)?) {
    val markerColor = MaterialTheme.colorScheme.primary.toArgb()
    val currentOnTap by rememberUpdatedState(onTap)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                mapboxMap.addOnMapClickListener { point ->
                    val tapped = LatLng(point.latitude(), point.longitude())
                    (tag as? MapState)?.lastTap = tapped
                    currentOnTap?.invoke(tapped)
                    currentOnTap != null
                }
            }
        },
        update = { map ->
            // One marker layer per map, kept on the view with the points it last framed
            val holder = map.tag as? MapState ?: MapState(map.annotations.createCircleAnnotationManager()).also { map.tag = it }
            val markers = holder.markers
            markers.deleteAll()
            markers.create(
                points.map {
                    CircleAnnotationOptions()
                        .withPoint(Point.fromLngLat(it.longitude, it.latitude))
                        .withCircleRadius(8.0)
                        .withCircleColor(markerColor)
                        .withCircleStrokeWidth(2.0)
                        .withCircleStrokeColor(android.graphics.Color.WHITE)
                },
            )
            // A point the user just tapped stays where it is; points from elsewhere (their
            // location, new photos) are brought into view
            val frame = !holder.framed || (holder.framedPoints != points && points != listOfNotNull(holder.lastTap))
            holder.framed = true
            holder.framedPoints = points
            if (!frame) return@AndroidView
            val geo = points.map { Point.fromLngLat(it.longitude, it.latitude) }
            val camera = when (geo.size) {
                0 -> CameraOptions.Builder().center(Point.fromLngLat(DefaultCenter.longitude, DefaultCenter.latitude)).zoom(DEFAULT_ZOOM).build()
                1 -> CameraOptions.Builder().center(geo.single()).zoom(SINGLE_POINT_ZOOM).build()
                else -> map.mapboxMap.cameraForCoordinates(geo, CameraOptions.Builder().build(), EdgeInsets(64.0, 64.0, 64.0, 64.0), null, null)
            }
            map.mapboxMap.setCamera(camera)
        },
    )
}

private class MapState(val markers: CircleAnnotationManager) {
    var framed = false
    var framedPoints: List<LatLng> = emptyList()
    var lastTap: LatLng? = null
}
