package com.uri.lee.dl.core.training

/**
 * The building blocks of a model's analysis export: a plain zip any tool can open, and embeddings
 * as a NumPy .npy file, so Python scripts can check and use a model trained in the app without it.
 */
object AnalysisBundle {

    /** [files] as a zip (stored, not compressed). */
    fun zip(files: Map<String, ByteArray>): ByteArray = StoredZip.append(ByteArray(0), files)

    /** [rows], each [columns] long, as a .npy file: little-endian float32, shape (rows, columns), for numpy.load. */
    fun npy(rows: List<FloatArray>, columns: Int): ByteArray {
        require(rows.all { it.size == columns }) { "Every row needs $columns values" }
        val dict = "{'descr': '<f4', 'fortran_order': False, 'shape': (${rows.size}, $columns), }"
        // Magic (6) + version (2) + header length (2) + header, padded with spaces and a newline to a multiple of 64
        val headerLength = (dict.length + 1 + 10 + 63) / 64 * 64 - 10
        val header = dict.padEnd(headerLength - 1) + "\n"
        val out = ByteArray(10 + headerLength + rows.size * columns * 4)
        byteArrayOf(0x93.toByte(), 'N'.code.toByte(), 'U'.code.toByte(), 'M'.code.toByte(), 'P'.code.toByte(), 'Y'.code.toByte(), 1, 0)
            .copyInto(out)
        out[8] = headerLength.toByte()
        out[9] = (headerLength shr 8).toByte()
        header.encodeToByteArray().copyInto(out, 10)
        var at = 10 + headerLength
        for (row in rows) for (v in row) {
            val bits = v.toRawBits()
            for (i in 0 until 4) out[at++] = (bits shr (8 * i)).toByte()
        }
        return out
    }
}
