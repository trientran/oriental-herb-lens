package com.uri.lee.dl.core.maps

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class LatLng(val latitude: Double, val longitude: Double)

/**
 * A map showing [points] as markers, framed to fit them (or the whole region when empty).
 * With [onTap], tapping the map reports that point, e.g. to choose where a photo was taken.
 */
@Composable
expect fun HerbMap(
    points: List<LatLng>,
    modifier: Modifier = Modifier,
    onTap: ((LatLng) -> Unit)? = null,
)

/** Shown when there are no points: Vietnam. */
internal val DefaultCenter = LatLng(16.0, 106.0)
internal const val DEFAULT_ZOOM = 4.5
internal const val SINGLE_POINT_ZOOM = 12.0
