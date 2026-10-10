package com.uri.lee.dl.core.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import com.mapbox.maps.plugin.gestures.gestures

@Composable
actual fun HerbMap(
    points: List<LatLng>,
    modifier: Modifier,
    interactive: Boolean,
    onCenterChanged: ((LatLng) -> Unit)?,
    showMarkers: Boolean,
    onPointClick: ((Int) -> Unit)?,
) {
    if (LocalInspectionMode.current) {
        // Previews and screenshot tests: Mapbox needs a real device
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
        return
    }
    val markerColor = MaterialTheme.colorScheme.primary.toArgb()
    val currentOnCenterChanged by rememberUpdatedState(onCenterChanged)
    val currentOnPointClick by rememberUpdatedState(onPointClick)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                gestures.updateSettings {
                    scrollEnabled = interactive
                    pinchToZoomEnabled = interactive
                    doubleTapToZoomInEnabled = interactive
                    doubleTouchToZoomOutEnabled = interactive
                    quickZoomEnabled = interactive
                    rotateEnabled = false
                    pitchEnabled = false
                }
                mapboxMap.subscribeMapIdle {
                    val center = mapboxMap.cameraState.center
                    currentOnCenterChanged?.invoke(LatLng(center.latitude(), center.longitude()))
                }
            }
        },
        update = { map ->
            // One marker layer per map, kept on the view with the points it last framed
            val state = map.tag as? MapState ?: MapState(map.annotations.createCircleAnnotationManager()).also { created ->
                map.tag = created
                created.markers.addClickListener { annotation ->
                    val index = created.indexById[annotation.id] ?: return@addClickListener false
                    currentOnPointClick?.invoke(index)
                    currentOnPointClick != null
                }
            }
            state.markers.deleteAll()
            state.indexById = if (!showMarkers) emptyMap() else state.markers.create(
                points.map {
                    CircleAnnotationOptions()
                        .withPoint(Point.fromLngLat(it.longitude, it.latitude))
                        // Bigger when it can be tapped
                        .withCircleRadius(if (onPointClick != null) 10.0 else 8.0)
                        .withCircleColor(markerColor)
                        .withCircleStrokeWidth(2.0)
                        .withCircleStrokeColor(android.graphics.Color.WHITE)
                },
            ).withIndex().associate { (index, annotation) -> annotation.id to index }
            // A map the user moves is framed once; a preview follows its points
            val frame = !state.framed || (!interactive && state.framedPoints != points)
            state.framed = true
            state.framedPoints = points
            if (!frame) return@AndroidView
            val geo = mainCluster(points).map { Point.fromLngLat(it.longitude, it.latitude) }
            // After the style has loaded: loading it applies the style's own camera (the whole globe),
            // which would replace one set earlier
            map.mapboxMap.getStyle {
                when (geo.size) {
                    0 -> map.mapboxMap.setCamera(
                        CameraOptions.Builder().center(Point.fromLngLat(DefaultCenter.longitude, DefaultCenter.latitude)).zoom(DEFAULT_ZOOM).build(),
                    )
                    1 -> map.mapboxMap.setCamera(CameraOptions.Builder().center(geo.single()).zoom(SINGLE_POINT_ZOOM).build())
                    // Fitting points also needs the map's size: this form waits for it
                    else -> map.mapboxMap.cameraForCoordinates(
                        geo, CameraOptions.Builder().build(), EdgeInsets(64.0, 64.0, 64.0, 64.0), SINGLE_POINT_ZOOM, null,
                    ) { camera -> map.mapboxMap.setCamera(camera) }
                }
            }
        },
    )
}

private class MapState(val markers: CircleAnnotationManager) {
    /** Each marker's index in the points, by its annotation id. */
    var indexById: Map<String, Int> = emptyMap()
    var framed = false
    var framedPoints: List<LatLng> = emptyList()
}
