package com.uri.lee.dl.core.training

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrowserNameTest {
    @Test
    fun chromeOnAnAppleSiliconMac() {
        val brands = listOf("Google Chrome" to "155.0.7790.12", "Chromium" to "155.0.7790.12", "Not.A/Brand" to "99.0.0.0")
        assertEquals("Chrome 155 on macOS 26.1 (arm64)", describeBrowser(brands, "macOS", "26.1.0", "arm", "64"))
    }

    @Test
    fun edgeOnWindows11() {
        val brands = listOf("Not)A;Brand" to "8.0.0.0", "Chromium" to "150.0.1.2", "Microsoft Edge" to "150.0.1.2")
        assertEquals("Edge 150 on Windows 11 (x86_64)", describeBrowser(brands, "Windows", "19.0.0", "x86", "64"))
    }

    @Test
    fun chromiumAloneAndMissingDetails() {
        assertEquals("Chromium 140 on Linux", describeBrowser(listOf("Chromium" to "140.0"), "Linux", "", "", ""))
        assertEquals("Chrome 155 on macOS 26", describeBrowser(listOf("Google Chrome" to "155"), "macOS", "26.0.0", null, null))
    }

    @Test
    fun noBrandIsNoName() {
        assertNull(describeBrowser(listOf("Not_A Brand" to "24"), "macOS", "26.1.0", "arm", "64"))
    }
}
