package com.uri.lee.dl.core.training

/**
 * Just enough of FlatBuffers (the format of .tflite files) to read a model's tables and build a
 * new one. Little-endian throughout, as the format requires.
 */
internal class FlatBufferReader(val bytes: ByteArray) {

    fun int(pos: Int): Int =
        (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8) or
            ((bytes[pos + 2].toInt() and 0xFF) shl 16) or ((bytes[pos + 3].toInt() and 0xFF) shl 24)

    fun short(pos: Int): Int = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8)

    fun byte(pos: Int): Int = bytes[pos].toInt() and 0xFF

    /** The root table's position. */
    val root: Int get() = int(0)

    /** Follows the offset stored at [pos] to what it points at. */
    fun deref(pos: Int): Int = pos + int(pos)

    /** Where field [id] of the table at [table] is stored, or null if it isn't. */
    fun field(table: Int, id: Int): Int? {
        val vtable = table - int(table)
        val vtableSize = short(vtable)
        val entry = 4 + 2 * id
        if (entry >= vtableSize) return null
        val offset = short(vtable + entry)
        return if (offset == 0) null else table + offset
    }

    fun intField(table: Int, id: Int, default: Int = 0): Int = field(table, id)?.let { int(it) } ?: default
    fun byteField(table: Int, id: Int, default: Int = 0): Int = field(table, id)?.let { byte(it) } ?: default

    /** Positions of the tables in the vector field [id] (empty if absent). */
    fun tables(table: Int, id: Int): List<Int> {
        val vector = field(table, id)?.let { deref(it) } ?: return emptyList()
        return List(int(vector)) { deref(vector + 4 + 4 * it) }
    }

    fun ints(table: Int, id: Int): List<Int> {
        val vector = field(table, id)?.let { deref(it) } ?: return emptyList()
        return List(int(vector)) { int(vector + 4 + 4 * it) }
    }

    fun string(table: Int, id: Int): String? {
        val s = field(table, id)?.let { deref(it) } ?: return null
        return bytes.decodeToString(s + 4, s + 4 + int(s))
    }
}

/**
 * Builds a FlatBuffer back to front, as the format intends: children first, so every reference
 * points forward. Offsets returned are measured from the end of the buffer, like the reference
 * implementation's.
 */
internal class FlatBufferBuilder(initialSize: Int = 1024) {
    private var buffer = ByteArray(initialSize)
    private var head = initialSize
    private var minAlign = 1
    private var fields = mutableListOf<Pair<Int, Int>>()
    private var tableStart = 0

    /** Bytes written so far. */
    fun offset(): Int = buffer.size - head

    private fun grow(needed: Int) {
        if (head >= needed) return
        var size = buffer.size
        while (size - offset() < needed) size *= 2
        val grown = ByteArray(size)
        buffer.copyInto(grown, size - offset(), head, buffer.size)
        head = size - offset()
        buffer = grown
    }

    /** Pads so that after [additional] more bytes, a value of [size] bytes is aligned. */
    fun prep(size: Int, additional: Int) {
        if (size > minAlign) minAlign = size
        val padding = (-(offset() + additional)).mod(size)
        grow(padding + additional + size)
        repeat(padding) { buffer[--head] = 0 }
    }

    private fun putInt(value: Int) {
        head -= 4
        buffer[head] = value.toByte()
        buffer[head + 1] = (value shr 8).toByte()
        buffer[head + 2] = (value shr 16).toByte()
        buffer[head + 3] = (value shr 24).toByte()
    }

    private fun putShort(value: Int) {
        head -= 2
        buffer[head] = value.toByte()
        buffer[head + 1] = (value shr 8).toByte()
    }

    private fun putByte(value: Int) {
        buffer[--head] = value.toByte()
    }

    fun addInt(value: Int) { prep(4, 0); putInt(value) }

    /** A reference to [target], written here. */
    fun addOffset(target: Int) {
        prep(4, 0)
        putInt(offset() + 4 - target)
    }

    /**
     * Copies [bytes] in whole, aligned to [align], and returns where it starts. Positions inside
     * it map to `start - position` (see [inBlob]).
     */
    fun addBlob(bytes: ByteArray, align: Int = 16): Int {
        prep(align, 0)
        // Pad the end so the blob's start, not just its end, falls on the alignment
        val tail = (-bytes.size).mod(align)
        grow(bytes.size + tail)
        repeat(tail) { buffer[--head] = 0 }
        head -= bytes.size
        bytes.copyInto(buffer, head)
        if (align > minAlign) minAlign = align
        return offset()
    }

    /** The builder offset of position [position] in a blob that starts at [blobStart]. */
    fun inBlob(blobStart: Int, position: Int): Int = blobStart - position

    fun createString(text: String): Int {
        val utf8 = text.encodeToByteArray()
        prep(4, utf8.size + 1)
        putByte(0)
        head -= utf8.size
        utf8.copyInto(buffer, head)
        putInt(utf8.size)
        return offset()
    }

    fun createIntVector(values: List<Int>): Int {
        prep(4, 4 * values.size)
        for (i in values.indices.reversed()) putInt(values[i])
        putInt(values.size)
        return offset()
    }

    fun createFloatVector(values: FloatArray): Int = createIntVector(values.map { it.toRawBits() })

    fun createOffsetVector(targets: List<Int>): Int {
        prep(4, 4 * targets.size)
        for (i in targets.indices.reversed()) addOffset(targets[i])
        putInt(targets.size)
        return offset()
    }

    /** A byte vector whose data is aligned to [align] (TFLite wants 16 for weights). */
    fun createByteVector(bytes: ByteArray, align: Int = 16): Int {
        prep(4, bytes.size)
        prep(align, bytes.size)
        grow(bytes.size + 4)
        head -= bytes.size
        bytes.copyInto(buffer, head)
        putInt(bytes.size)
        return offset()
    }

    fun startTable() {
        fields = mutableListOf()
        tableStart = offset()
    }

    fun intField(id: Int, value: Int) { addInt(value); fields += id to offset() }
    fun byteField(id: Int, value: Int) { prep(1, 0); grow(1); putByte(value); fields += id to offset() }
    fun floatField(id: Int, value: Float) = intField(id, value.toRawBits())
    fun offsetField(id: Int, target: Int) { addOffset(target); fields += id to offset() }

    fun endTable(): Int {
        addInt(0) // where the vtable reference goes
        val table = offset()
        val slots = (fields.maxOfOrNull { it.first } ?: -1) + 1
        val offsets = IntArray(slots)
        for ((id, at) in fields) offsets[id] = table - at
        grow(4 + 2 * slots)
        for (i in slots - 1 downTo 0) putShort(offsets[i])
        putShort(table - tableStart)
        putShort(4 + 2 * slots)
        val vtable = offset()
        // The table points back to its vtable: table position minus vtable position
        val at = buffer.size - table
        val value = vtable - table
        buffer[at] = value.toByte()
        buffer[at + 1] = (value shr 8).toByte()
        buffer[at + 2] = (value shr 16).toByte()
        buffer[at + 3] = (value shr 24).toByte()
        return table
    }

    /** Finishes with [root] as the root table and [identifier] (4 characters) after it. */
    fun finish(root: Int, identifier: String): ByteArray {
        prep(minAlign, 8)
        val id = identifier.encodeToByteArray()
        require(id.size == 4) { "Identifier must be 4 bytes" }
        for (i in 3 downTo 0) putByte(id[i].toInt())
        addOffset(root)
        return buffer.copyOfRange(head, buffer.size)
    }
}

internal fun FloatArray.toLittleEndianBytes(): ByteArray {
    val out = ByteArray(size * 4)
    for (i in indices) {
        val bits = this[i].toRawBits()
        out[4 * i] = bits.toByte()
        out[4 * i + 1] = (bits shr 8).toByte()
        out[4 * i + 2] = (bits shr 16).toByte()
        out[4 * i + 3] = (bits shr 24).toByte()
    }
    return out
}
