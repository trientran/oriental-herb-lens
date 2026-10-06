package com.uri.lee.dl.core.training

/**
 * TFLite model metadata (tensorflow/tflite-support metadata_schema.fbs), the description tools
 * like MediaPipe, ML Kit and Android Studio read to use an image classifier without code: the
 * input is an RGB image normalised as (pixel - 0) / 255, the output is one score per label, and
 * the labels are `labels.txt` packed in the file (see [StoredZip]).
 */
internal object ModelMetadata {

    fun build(name: String, description: String, author: String, version: String, inputSize: Int, labelsFile: String): ByteArray {
        val b = FlatBufferBuilder()

        // Input: an RGB image, pixels 0–255 mapped to 0–1
        val mean = b.createFloatVector(floatArrayOf(0f))
        val std = b.createFloatVector(floatArrayOf(255f))
        b.startTable(); b.offsetField(0, mean); b.offsetField(1, std); val normalization = b.endTable()
        b.startTable(); b.byteField(0, PROCESS_NORMALIZATION); b.offsetField(1, normalization); val processUnit = b.endTable()
        b.startTable(); b.intField(0, inputSize); b.intField(1, inputSize); val imageSize = b.endTable()
        b.startTable(); b.byteField(0, COLOR_RGB); b.offsetField(1, imageSize); val imageProperties = b.endTable()
        b.startTable(); b.byteField(0, CONTENT_IMAGE); b.offsetField(1, imageProperties); val inputContent = b.endTable()
        val inputName = b.createString("image")
        val inputDescription = b.createString("RGB image, $inputSize × $inputSize")
        val inputProcessUnits = b.createOffsetVector(listOf(processUnit))
        b.startTable()
        b.offsetField(0, inputName)
        b.offsetField(1, inputDescription)
        b.offsetField(3, inputContent)
        b.offsetField(4, inputProcessUnits)
        val input = b.endTable()

        // Output: a probability per label, labels in the packed file
        val fileName = b.createString(labelsFile)
        val fileDescription = b.createString("Class names, one per line, in output order")
        b.startTable(); b.offsetField(0, fileName); b.offsetField(1, fileDescription); b.byteField(2, FILE_TENSOR_AXIS_LABELS); val labels = b.endTable()
        b.startTable(); val features = b.endTable()
        b.startTable(); b.byteField(0, CONTENT_FEATURE); b.offsetField(1, features); val outputContent = b.endTable()
        val outputName = b.createString("probability")
        val outputDescription = b.createString("Probability of each class")
        val outputFiles = b.createOffsetVector(listOf(labels))
        b.startTable()
        b.offsetField(0, outputName)
        b.offsetField(1, outputDescription)
        b.offsetField(3, outputContent)
        b.offsetField(6, outputFiles)
        val output = b.endTable()

        val inputs = b.createOffsetVector(listOf(input))
        val outputs = b.createOffsetVector(listOf(output))
        b.startTable(); b.offsetField(2, inputs); b.offsetField(3, outputs); val subgraph = b.endTable()

        val nameOffset = b.createString(name)
        val descriptionOffset = b.createString(description)
        val versionOffset = b.createString(version)
        val authorOffset = b.createString(author)
        val subgraphs = b.createOffsetVector(listOf(subgraph))
        val parser = b.createString("1.0.0")
        b.startTable()
        b.offsetField(0, nameOffset)
        b.offsetField(1, descriptionOffset)
        b.offsetField(2, versionOffset)
        b.offsetField(3, subgraphs)
        b.offsetField(4, authorOffset)
        b.offsetField(7, parser)
        return b.finish(b.endTable(), "M001")
    }

    private const val CONTENT_FEATURE = 1
    private const val CONTENT_IMAGE = 2
    private const val COLOR_RGB = 1
    private const val PROCESS_NORMALIZATION = 1
    private const val FILE_TENSOR_AXIS_LABELS = 2
}

/**
 * A zip archive of uncompressed ("stored") files, appended to a .tflite the way the TFLite
 * metadata writer packs associated files: offsets count from the start of the whole file, so the
 * model stays a valid FlatBuffer and `unzip model.tflite` still lists the files.
 */
internal object StoredZip {

    fun append(model: ByteArray, files: Map<String, ByteArray>): ByteArray {
        val out = ByteList(model.size + files.values.sumOf { it.size } + 256)
        out.add(model)
        val central = ByteList(256)
        for ((name, data) in files) {
            val nameBytes = name.encodeToByteArray()
            val crc = crc32(data)
            val offset = out.size
            out.int(0x04034b50); out.short(10); out.short(0); out.short(0); out.short(0); out.short(0x21)
            out.int(crc); out.int(data.size); out.int(data.size); out.short(nameBytes.size); out.short(0)
            out.add(nameBytes); out.add(data)
            central.int(0x02014b50); central.short(20); central.short(10); central.short(0); central.short(0); central.short(0); central.short(0x21)
            central.int(crc); central.int(data.size); central.int(data.size); central.short(nameBytes.size)
            central.short(0); central.short(0); central.short(0); central.short(0); central.int(0); central.int(offset)
            central.add(nameBytes)
        }
        val centralStart = out.size
        out.add(central.toByteArray())
        out.int(0x06054b50); out.short(0); out.short(0); out.short(files.size); out.short(files.size)
        out.int(central.size); out.int(centralStart); out.short(0)
        return out.toByteArray()
    }

    /** A stored file from the zip at the end of [bytes], or null. */
    fun read(bytes: ByteArray, name: String): ByteArray? {
        val r = FlatBufferReader(bytes)
        if (bytes.size < 22) return null
        val end = (bytes.size - 22 downTo maxOf(0, bytes.size - 65_557)).firstOrNull { r.int(it) == 0x06054b50 } ?: return null
        var entry = r.int(end + 16)
        repeat(r.short(end + 10)) {
            val nameLength = r.short(entry + 28)
            if (bytes.decodeToString(entry + 46, entry + 46 + nameLength) == name) {
                if (r.short(entry + 10) != 0) return null // compressed
                val size = r.int(entry + 20)
                val local = r.int(entry + 42)
                val start = local + 30 + r.short(local + 26) + r.short(local + 28)
                return bytes.copyOfRange(start, start + size)
            }
            entry += 46 + nameLength + r.short(entry + 30) + r.short(entry + 32)
        }
        return null
    }

    private val table = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
        c
    }

    fun crc32(data: ByteArray): Int {
        var c = -1
        for (byte in data) c = table[(c xor byte.toInt()) and 0xFF] xor (c ushr 8)
        return c.inv()
    }

    private class ByteList(capacity: Int) {
        private var bytes = ByteArray(capacity)
        var size = 0
            private set

        private fun ensure(extra: Int) {
            if (size + extra > bytes.size) bytes = bytes.copyOf(maxOf(bytes.size * 2, size + extra))
        }

        fun add(data: ByteArray) { ensure(data.size); data.copyInto(bytes, size); size += data.size }
        fun short(v: Int) { ensure(2); bytes[size++] = v.toByte(); bytes[size++] = (v shr 8).toByte() }
        fun int(v: Int) { short(v and 0xFFFF); short(v ushr 16) }
        fun toByteArray() = bytes.copyOf(size)
    }
}
