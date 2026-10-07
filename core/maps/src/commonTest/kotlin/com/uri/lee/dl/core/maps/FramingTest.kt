package com.uri.lee.dl.core.maps

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FramingTest {

    // Musk okra's photo places: mostly Taiwan, with Malaysia, central Vietnam and Colombia
    private val taiwan = listOf(
        LatLng(23.47567, 120.495895), LatLng(22.967303, 120.439025), LatLng(23.254483, 120.687417),
        LatLng(22.657292, 120.285767), LatLng(23.826709, 120.801842), LatLng(23.4758, 120.495828),
        LatLng(22.702547, 121.017253),
    )
    private val centralVietnam = LatLng(15.941877, 108.513669)
    private val malaysia = LatLng(3.718579, 101.77312)
    private val colombia = LatLng(8.522186, -76.549188)

    @Test
    fun `the map frames where most photos were taken and not every outlier`() {
        // Central Vietnam is within reach of Taiwan (about 1,400 km); Malaysia and Colombia aren't
        assertEquals((taiwan + centralVietnam).toSet(), mainCluster(taiwan + malaysia + centralVietnam + colombia).toSet())
    }

    @Test
    fun `places close together are all framed`() {
        val vietnam = listOf(LatLng(21.03, 105.85), LatLng(16.05, 108.2), LatLng(10.78, 106.7))
        assertEquals(vietnam, mainCluster(vietnam))
    }

    @Test
    fun `one or two places are framed as they are`() {
        val two = listOf(LatLng(21.03, 105.85), LatLng(8.52, -76.55))
        assertEquals(two, mainCluster(two))
    }

    @Test
    fun `distances are great-circle kilometres`() {
        val hanoiToSaigon = distanceKm(LatLng(21.03, 105.85), LatLng(10.78, 106.7))
        assertTrue(hanoiToSaigon in 1_130.0..1_160.0, "was $hanoiToSaigon")
    }
}
