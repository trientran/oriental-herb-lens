package com.uri.lee.dl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pins the Firestore herb document schema read by [herbFromFields]. */
class HerbMappingTest {

    private val schemaFields = listOf(
        "enDosing", "enInteractions", "enName", "enOverview", "enSideEffects", "latinName",
        "viDosing", "viInteractions", "viName", "viOverview", "viSideEffects",
    )

    @Test
    fun `every schema field is read from the field of the same name`() {
        val herb = herbFromFields("42") { field -> "value of $field" }

        val actual = mapOf(
            "enDosing" to herb.enDosing,
            "enInteractions" to herb.enInteractions,
            "enName" to herb.enName,
            "enOverview" to herb.enOverview,
            "enSideEffects" to herb.enSideEffects,
            "latinName" to herb.latinName,
            "viDosing" to herb.viDosing,
            "viInteractions" to herb.viInteractions,
            "viName" to herb.viName,
            "viOverview" to herb.viOverview,
            "viSideEffects" to herb.viSideEffects,
        )
        assertEquals(schemaFields.associateWith { "value of $it" }, actual)
    }

    @Test
    fun `document id becomes both id and Algolia objectID`() {
        val herb = herbFromFields("42") { null }

        assertEquals("42", herb.id)
        assertEquals("42", herb.objectID)
    }

    @Test
    fun `missing fields stay null and Vietnamese text passes through unchanged`() {
        val herb = herbFromFields("7") { field -> if (field == "viName") "Đinh lăng" else null }

        assertEquals("Đinh lăng", herb.viName)
        assertNull(herb.latinName)
        assertNull(herb.images)
    }
}
