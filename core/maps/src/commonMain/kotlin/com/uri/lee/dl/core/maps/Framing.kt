package com.uri.lee.dl.core.maps

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The points a map frames: the biggest group within [radiusKm] of one of them, so one far-off photo
 * (a GBIF observation on another continent, say) doesn't zoom the map out to the whole globe. The
 * other points are still drawn, off-screen until the map is moved.
 */
internal fun mainCluster(points: List<LatLng>, radiusKm: Double = CLUSTER_RADIUS_KM): List<LatLng> {
    if (points.size < 3) return points
    val neighbours = points.map { centre -> points.filter { distanceKm(centre, it) <= radiusKm } }
    // The most neighbours; ties go to the first point
    return neighbours.maxBy { it.size }
}

/** Great-circle distance (haversine). */
internal fun distanceKm(a: LatLng, b: LatLng): Double {
    fun rad(degrees: Double) = degrees * PI / 180
    val dLat = rad(b.latitude - a.latitude)
    val dLon = rad(b.longitude - a.longitude)
    val h = sin(dLat / 2).pow(2) + cos(rad(a.latitude)) * cos(rad(b.latitude)) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_KM * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

internal const val CLUSTER_RADIUS_KM = 1_500.0
private const val EARTH_RADIUS_KM = 6_371.0
