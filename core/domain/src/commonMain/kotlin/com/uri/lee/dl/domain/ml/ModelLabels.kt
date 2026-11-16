package com.uri.lee.dl.domain.ml

/**
 * Reads an uncompressed ("stored") entry, such as labels.txt, from the zip archive TFLite metadata
 * appends to a model. [tail] is the end of the file, starting at byte [tailOffset]: the archive
 * sits at the end, so a few hundred kilobytes are enough without reading the whole model. Null when
 * the entry isn't there, is compressed, or starts before [tail].
 */
fun storedZipEntry(tail: ByteArray, tailOffset: Long, name: String): String? {
    fun u16(at: Int) = (tail[at].toInt() and 0xff) or ((tail[at + 1].toInt() and 0xff) shl 8)
    fun u32(at: Int) = (u16(at).toLong() or (u16(at + 2).toLong() shl 16))
    fun local(absolute: Long): Int? = (absolute - tailOffset).takeIf { it >= 0 && it < tail.size }?.toInt()
    if (tail.size < 22) return null
    // End of central directory: the last "PK\u0005\u0006"
    val end = (tail.size - 22 downTo maxOf(0, tail.size - 65_557)).firstOrNull { u32(it) == 0x06054b50L } ?: return null
    var entry = local(u32(end + 16)) ?: return null
    repeat(u16(end + 10)) {
        if (entry + 46 > tail.size) return null
        val nameLength = u16(entry + 28)
        val entryName = tail.decodeToString(entry + 46, entry + 46 + nameLength)
        if (entryName.trimStart('/') == name) {
            if (u16(entry + 10) != 0) return null // compressed: not what the metadata writer produces
            val size = u32(entry + 20).toInt()
            val header = local(u32(entry + 42)) ?: return null
            val start = header + 30 + u16(header + 26) + u16(header + 28)
            if (start + size > tail.size) return null
            return tail.decodeToString(start, start + size)
        }
        entry += 46 + nameLength + u16(entry + 30) + u16(entry + 32)
    }
    return null
}

/** How much of a model's end to read for its labels: the label list of a few thousand species is well under this. */
const val LABELS_TAIL_BYTES = 1024 * 1024
