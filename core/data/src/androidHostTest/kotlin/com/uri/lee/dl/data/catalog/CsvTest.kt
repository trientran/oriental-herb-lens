package com.uri.lee.dl.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {

    @Test
    fun `plain fields and both line endings`() {
        assertEquals(
            listOf(listOf("a", "b"), listOf("c", "d"), listOf("e", "f")),
            Csv.parse("a,b\r\nc,d\ne,f"),
        )
    }

    @Test
    fun `quoted fields keep commas, doubled quotes and line breaks`() {
        val rows = Csv.parse("id,note\n1,\"R. Br. (1818). In: Narr., 376\"\n2,\"say \"\"hi\"\"\"\n3,\"two\r\nlines\"\n")

        assertEquals(listOf("1", "R. Br. (1818). In: Narr., 376"), rows[1])
        assertEquals(listOf("2", "say \"hi\""), rows[2])
        assertEquals(listOf("3", "two\r\nlines"), rows[3])
    }

    @Test
    fun `empty fields are kept`() {
        assertEquals(listOf(listOf("a", "", "", "d")), Csv.parse("a,,,d"))
        assertEquals(listOf(listOf("", "")), Csv.parse(",\n"))
    }

    @Test
    fun `trailing newline adds no row and a BOM is ignored`() {
        assertEquals(listOf(listOf("x", "y")), Csv.parse("\uFEFFx,y\r\n"))
    }
}
