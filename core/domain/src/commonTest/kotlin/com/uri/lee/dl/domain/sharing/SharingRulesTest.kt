package com.uri.lee.dl.domain.sharing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharingRulesTest {

    private val species = listOf("Bạc hà", "Mentha arvensis", "Ocimum basilicum")

    @Test
    fun `a plain model can be shared`() {
        assertNull(SharingRules.problem("Garden herbs 2026", species, 12_000_000))
    }

    @Test
    fun `names must fit and a model needs two species`() {
        assertEquals(SharingProblem.NAME_LENGTH, SharingRules.problem(" ", species, 1))
        assertEquals(SharingProblem.NAME_LENGTH, SharingRules.problem("x".repeat(61), species, 1))
        assertEquals(SharingProblem.TOO_FEW_SPECIES, SharingRules.problem("Mint", listOf("Mint"), 1))
        assertEquals(SharingProblem.TOO_LARGE, SharingRules.problem("Mint", species, SharingRules.MAX_BYTES + 1))
    }

    @Test
    fun `contact details are refused, in the name or a species`() {
        assertEquals(SharingProblem.CONTACT_DETAILS, SharingRules.problem("Ask me: me@example.com", species, 1))
        assertEquals(SharingProblem.CONTACT_DETAILS, SharingRules.problem("Herbs", species + "see www.shop.vn", 1))
        assertEquals(SharingProblem.CONTACT_DETAILS, SharingRules.problem("Call 0912 345 678", species, 1))
    }

    @Test
    fun `obvious abuse is refused, but ordinary words that fold to it are not`() {
        assertEquals(SharingProblem.OFFENSIVE_WORDS, SharingRules.problem("shit plants", species, 1))
        assertEquals(SharingProblem.OFFENSIVE_WORDS, SharingRules.problem("Cây", species + "đéo biết", 1))
        // "các" and "lon" fold to Vietnamese abuse without their diacritics
        assertNull(SharingRules.problem("Các loại rau", species + "Lon nước", 1))
    }
}
