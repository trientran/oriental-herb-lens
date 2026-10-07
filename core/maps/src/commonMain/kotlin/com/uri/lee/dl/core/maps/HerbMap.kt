package com.uri.lee.dl.core.maps

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class LatLng(val latitude: Double, val longitude: Double)

/**
 * A map showing [points] as markers, framed to fit them (or the whole region when empty).
 *
 * Not [interactive] by default: inside a scrolling screen a draggable map would fight the page
 * for every touch. An interactive map can be dragged and zoomed, frames [points] once, and reports
 * its centre through [onCenterChanged] whenever it comes to rest, e.g. under a fixed pin.
 */
@Composable
expect fun HerbMap(
    points: List<LatLng>,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    onCenterChanged: ((LatLng) -> Unit)? = null,
    /** False frames [points] without drawing them, e.g. to start a place picker where it was. */
    showMarkers: Boolean = true,
    /** A marker was tapped: its index in [points]. Works on a map that isn't [interactive] too. */
    onPointClick: ((Int) -> Unit)? = null,
)

/** Shown when there are no points: Vietnam. */
internal val DefaultCenter = LatLng(16.0, 106.0)
internal const val DEFAULT_ZOOM = 4.5
internal const val SINGLE_POINT_ZOOM = 12.0
