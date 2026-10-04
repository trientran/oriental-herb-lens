package com.uri.lee.dl.data.catalog

/**
 * Minimal RFC 4180 reader: fields are comma-separated; a quoted field may contain commas,
 * line breaks and doubled quotes (`""`). Accepts CRLF or LF line endings and a leading BOM.
 */
internal object Csv {

    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = if (text.startsWith('\uFEFF')) 1 else 0

        fun endField() {
            row.add(field.toString())
            field.clear()
        }

        fun endRow() {
            endField()
            // A trailing newline produces one empty field; don't report it as a row.
            if (!(row.size == 1 && row[0].isEmpty())) rows.add(row)
            row = mutableListOf()
        }

        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> endField()
                    '\r' -> {
                        endRow()
                        if (i + 1 < text.length && text[i + 1] == '\n') i++
                    }
                    '\n' -> endRow()
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
}
