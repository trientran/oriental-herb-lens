package com.uri.lee.dl.core.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

/**
 * OpenStreetMap tiles drawn by Compose itself, with the same framing and centre reporting as the
 * apps' maps. (An HTML map would sit above the app's canvas and hide whatever Compose draws over
 * it, like the place picker's pin or a dialog.)
 */
@OptIn(FlowPreview::class)
@Composable
actual fun HerbMap(
    points: List<LatLng>,
    modifier: Modifier,
    interactive: Boolean,
    onCenterChanged: ((LatLng) -> Unit)?,
    showMarkers: Boolean,
) {
    val currentOnCenterChanged by rememberUpdatedState(onCenterChanged)
    var centerX by remember { mutableDoubleStateOf(mercatorX(DefaultCenter.longitude)) }
    var centerY by remember { mutableDoubleStateOf(mercatorY(DefaultCenter.latitude)) }
    var zoom by remember { mutableDoubleStateOf(DEFAULT_ZOOM) }
    var framedPoints by remember { mutableStateOf<List<LatLng>?>(null) }
    val density = LocalDensity.current.density

    BoxWithConstraints(modifier.clipToBounds().background(Color(0xFFDDE5D8))) {
        val width = constraints.maxWidth.toDouble()
        val height = constraints.maxHeight.toDouble()
        // A map the user moves is framed once; a preview follows its points
        if (framedPoints == null || (!interactive && framedPoints != points)) {
            framedPoints = points
            val (x, y, z) = frame(points, width / density, height / density)
            centerX = x
            centerY = y
            zoom = z
        }
        if (interactive) {
            LaunchedEffect(Unit) {
                // Like a map's "move end": report the centre once the map comes to rest
                snapshotFlow { centerX to centerY }.drop(1).debounce(250).collect { (x, y) ->
                    currentOnCenterChanged?.invoke(LatLng(latitude(y), longitude(x)))
                }
            }
        }

        val tileZoom = floor(zoom).toInt().coerceIn(0, MAX_ZOOM)
        val tiles = 1 shl tileZoom
        val world = TILE * 2.0.pow(zoom) * density // the whole world, in pixels, at this zoom
        val tileSize = world / tiles
        val left = centerX * world - width / 2
        val top = centerY * world - height / 2
        val gestures = if (!interactive) Modifier else Modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoomChange, _ ->
                    val w = TILE * 2.0.pow(zoom) * density
                    centerX = (centerX - pan.x / w).mod(1.0)
                    centerY = (centerY - pan.y / w).coerceIn(0.0, 1.0)
                    zoom = (zoom + log2(zoomChange.toDouble())).coerceIn(MIN_ZOOM, MAX_ZOOM.toDouble())
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val delta = event.changes.first().scrollDelta.y
                            zoom = (zoom - delta * 0.5).coerceIn(MIN_ZOOM, MAX_ZOOM.toDouble())
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
        Box(gestures.requiredSize(maxWidth, maxHeight)) {
            val tileDp = (tileSize / density).dp
            for (tx in floor(left / tileSize).toInt()..floor((left + width) / tileSize).toInt()) {
                for (ty in maxOf(0, floor(top / tileSize).toInt())..minOf(tiles - 1, floor((top + height) / tileSize).toInt())) {
                    key(tileZoom, tx, ty) {
                        AsyncImage(
                            model = "https://tile.openstreetmap.org/$tileZoom/${tx.mod(tiles)}/$ty.png",
                            contentDescription = null,
                            modifier = Modifier
                                .offset { IntOffset((tx * tileSize - left).roundToInt(), (ty * tileSize - top).roundToInt()) }
                                .requiredSize(tileDp),
                        )
                    }
                }
            }
            if (showMarkers) points.forEach { point ->
                val x = mercatorX(point.longitude) * world - left
                val y = mercatorY(point.latitude) * world - top
                Box(
                    Modifier
                        .offset { IntOffset((x - MARKER_PX * density / 2).roundToInt(), (y - MARKER_PX * density / 2).roundToInt()) }
                        .size(MARKER_PX.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .border(2.dp, Color.White, CircleShape),
                )
            }
        }
        if (interactive) {
            Column(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                SmallFloatingActionButton(onClick = { zoom = (zoom + 1).coerceAtMost(MAX_ZOOM.toDouble()) }) { Text("+", fontSize = 20.sp) }
                SmallFloatingActionButton(onClick = { zoom = (zoom - 1).coerceAtLeast(MIN_ZOOM) }) { Text("−", fontSize = 20.sp) }
            }
        }
        // Required by OpenStreetMap's tile policy
        val uriHandler = LocalUriHandler.current
        Text(
            "© OpenStreetMap",
            fontSize = 10.sp,
            color = Color.Black,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(topStart = 4.dp))
                .clickable { uriHandler.openUri("https://www.openstreetmap.org/copyright") }
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

/** Where to centre and how far to zoom so [points] fit a [width] × [height] dp map. */
private fun frame(points: List<LatLng>, width: Double, height: Double): Triple<Double, Double, Double> = when (points.size) {
    0 -> Triple(mercatorX(DefaultCenter.longitude), mercatorY(DefaultCenter.latitude), DEFAULT_ZOOM)
    1 -> Triple(mercatorX(points[0].longitude), mercatorY(points[0].latitude), SINGLE_POINT_ZOOM)
    else -> {
        val xs = points.map { mercatorX(it.longitude) }
        val ys = points.map { mercatorY(it.latitude) }
        val spanX = (xs.max() - xs.min()).coerceAtLeast(1e-6)
        val spanY = (ys.max() - ys.min()).coerceAtLeast(1e-6)
        val padding = 64.0
        val z = minOf(log2((width - padding) / (spanX * TILE)), log2((height - padding) / (spanY * TILE)))
        Triple((xs.max() + xs.min()) / 2, (ys.max() + ys.min()) / 2, z.coerceIn(MIN_ZOOM, SINGLE_POINT_ZOOM))
    }
}

// Web Mercator, as fractions (0..1) of the world's width and height
private fun mercatorX(longitude: Double) = (longitude + 180) / 360
private fun mercatorY(latitude: Double): Double {
    val rad = latitude.coerceIn(-85.0, 85.0) * PI / 180
    return (1 - ln(tan(rad) + 1 / cos(rad)) / PI) / 2
}
private fun longitude(x: Double) = x * 360 - 180
private fun latitude(y: Double) = atan(sinh(PI * (1 - 2 * y))) * 180 / PI

private const val TILE = 256.0
private const val MIN_ZOOM = 1.0
private const val MAX_ZOOM = 19
private const val MARKER_PX = 16
