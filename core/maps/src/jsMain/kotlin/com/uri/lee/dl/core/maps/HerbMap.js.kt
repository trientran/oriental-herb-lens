package com.uri.lee.dl.core.maps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlinx.browser.document
import org.w3c.dom.HTMLDivElement
import kotlin.js.json

@JsModule("leaflet")
@JsNonModule
private external object Leaflet {
    fun map(element: HTMLDivElement, options: dynamic): dynamic
    fun tileLayer(url: String, options: dynamic): dynamic
    fun circleMarker(latLng: Array<Double>, options: dynamic): dynamic
    fun layerGroup(): dynamic
    fun latLngBounds(points: Array<Array<Double>>): dynamic
}

// Leaflet's own styles, bundled by webpack
private val leafletCss = js("require('leaflet/dist/leaflet.css')")

/** Leaflet with OpenStreetMap tiles, with the same framing and centre reporting as the apps' maps. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun HerbMap(
    points: List<LatLng>,
    modifier: Modifier,
    interactive: Boolean,
    onCenterChanged: ((LatLng) -> Unit)?,
    showMarkers: Boolean,
) {
    leafletCss
    val currentOnCenterChanged by rememberUpdatedState(onCenterChanged)
    val holder = remember { MapHolder(interactive) }
    holder.onCenterChanged = { currentOnCenterChanged?.invoke(it) }
    HtmlElementView(
        factory = { holder.element },
        modifier = modifier,
        update = { holder.show(points, showMarkers) },
    )
    DisposableEffect(Unit) { onDispose { holder.dispose() } }
}

private class MapHolder(private val interactive: Boolean) {
    var onCenterChanged: (LatLng) -> Unit = {}
    val element = (document.createElement("div") as HTMLDivElement).apply {
        style.width = "100%"
        style.height = "100%"
    }
    private val map: dynamic = run {
        val map: dynamic = Leaflet.map(
            element,
            json(
                "dragging" to interactive, "touchZoom" to interactive, "scrollWheelZoom" to interactive,
                "doubleClickZoom" to interactive, "boxZoom" to interactive, "keyboard" to interactive,
                "zoomControl" to interactive, "attributionControl" to true,
            ),
        )
        Leaflet.tileLayer(
            "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
            json("maxZoom" to 19, "attribution" to "© <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a>"),
        ).addTo(map)
        map.on("moveend") { _: dynamic ->
            val center = map.getCenter()
            onCenterChanged(LatLng(center.lat as Double, center.lng as Double))
        }
        map
    }
    private val markers: dynamic = Leaflet.layerGroup().addTo(map)
    // Leaflet measures its element once; tell it when the element is laid out or resized
    private val resizeObserver: dynamic = run {
        val onResize: () -> Unit = { map.invalidateSize() }
        val observer = js("ResizeObserver")
        val created: dynamic = js("new observer(onResize)")
        created.observe(element)
        created
    }

    private var framed = false
    private var framedPoints: List<LatLng> = emptyList()

    fun show(points: List<LatLng>, showMarkers: Boolean) {
        markers.clearLayers()
        if (showMarkers) points.forEach { point ->
            Leaflet.circleMarker(
                arrayOf(point.latitude, point.longitude),
                json("radius" to 8, "color" to "#ffffff", "weight" to 2, "fillColor" to "#2e7d32", "fillOpacity" to 1),
            ).addTo(markers)
        }
        // A map the user moves is framed once; a preview follows its points
        if (framed && (interactive || framedPoints == points)) return
        framed = true
        framedPoints = points
        when (points.size) {
            0 -> map.setView(arrayOf(DefaultCenter.latitude, DefaultCenter.longitude), DEFAULT_ZOOM)
            1 -> map.setView(arrayOf(points[0].latitude, points[0].longitude), SINGLE_POINT_ZOOM)
            else -> map.fitBounds(
                Leaflet.latLngBounds(points.map { arrayOf(it.latitude, it.longitude) }.toTypedArray()),
                json("padding" to arrayOf(32, 32), "maxZoom" to SINGLE_POINT_ZOOM),
            )
        }
    }

    fun dispose() {
        resizeObserver.disconnect()
        map.remove()
    }
}
