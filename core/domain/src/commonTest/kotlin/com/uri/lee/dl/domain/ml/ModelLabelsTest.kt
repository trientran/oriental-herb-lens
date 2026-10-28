package com.uri.lee.dl.domain.ml

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelLabelsTest {

    /** 100 bytes standing in for a model, then a stored zip holding labels.txt, as TFLite metadata appends it. */
    @OptIn(ExperimentalEncodingApi::class)
    private val model = Base64.decode(
        "VEZMM1RGTDNURkwzVEZMM1RGTDNURkwzVEZMM1RGTDNURkwzVEZMM1RGTDNURkwzVEZMM1RGTDNURkwzVEZMM1RGTDNURkwzVEZMM1RGTDNURk" +
            "wzVEZMM1RGTDNURkwzVEZMM1BLAwQUAAAAAAAYclpdCJP9aBoAAAAaAAAACgAAAGxhYmVscy50eHQzMDM1NjUyCjI3NjYyNzgKbm90IGEga2V5" +
            "ClBLAQIUAxQAAAAAABhyWl0Ik/1oGgAAABoAAAAKAAAAAAAAAAAAAACAAWQAAABsYWJlbHMudHh0UEsFBgAAAAABAAEAOAAAAKYAAAAAAA==",
    )

    @Test
    fun readsTheLabelsAndKeepsOnlySpeciesKeys() {
        assertEquals(setOf(3035652L, 2766278L), speciesKeysOf(storedZipEntry(model, 0, "labels.txt")))
    }

    @Test
    fun readsTheLabelsFromTheEndOfTheFileAlone() {
        val tail = model.copyOfRange(90, model.size)
        assertEquals(setOf(3035652L, 2766278L), speciesKeysOf(storedZipEntry(tail, 90, "labels.txt")))
    }

    @Test
    fun aTailTooShortOrAMissingEntryGivesNothing() {
        assertNull(storedZipEntry(model.copyOfRange(150, model.size), 150, "labels.txt"))
        assertNull(storedZipEntry(model, 0, "other.txt"))
        assertNull(storedZipEntry(ByteArray(10), 0, "labels.txt"))
        assertEquals(emptySet(), speciesKeysOf(null))
    }
}
