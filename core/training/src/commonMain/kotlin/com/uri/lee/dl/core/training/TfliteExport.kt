package com.uri.lee.dl.core.training

/**
 * Turns a trained head into a standard LiteRT (.tflite) model: the backbone's own graph with the
 * head's layers added after its embedding, so one file goes from image to class probabilities
 * and runs anywhere LiteRT or TensorFlow Lite does. The input is the backbone's (224 × 224 RGB,
 * float 0–1 for the MediaPipe MobileNets); the output is one probability per class, in the order
 * of the labels saved with it.
 *
 * The file stands alone: it carries standard TFLite metadata (input normalisation, output
 * labels) and the class names as `labels.txt` packed inside, so MediaPipe, ML Kit or this app can
 * use it as an image classifier with nothing else.
 *
 * The backbone file is copied in whole and the new model's tables point into it, so its layers
 * and their options are kept exactly as they were, whatever operators it uses. Its metadata and
 * signatures, which describe an embedder, are left out.
 */
object TfliteExport {

    const val LABELS_FILE = "labels.txt"

    fun export(
        backbone: ByteArray,
        head: ClassifierHead,
        labels: List<String>,
        name: String = "Herb Lens user-trained classifier",
        description: String = "",
        author: String = "",
        version: String = "1",
        /** More files to pack inside, next to labels.txt (TFLite tools ignore them). */
        extraFiles: Map<String, ByteArray> = emptyMap(),
    ): ByteArray {
        require(labels.size == head.classes) { "${labels.size} labels for ${head.classes} classes" }
        require(labels.none { '\n' in it || '\r' in it }) { "Labels can't contain line breaks" }
        val model = FlatBufferReader(backbone)
        val root = model.root
        require(backbone.decodeToString(4, 8) == "TFL3") { "Not a .tflite model" }
        val subgraphs = model.tables(root, MODEL_SUBGRAPHS)
        require(subgraphs.size == 1) { "Backbones with ${subgraphs.size} subgraphs aren't supported" }
        val graph = subgraphs.single()
        val outputs = model.ints(graph, SUBGRAPH_OUTPUTS)
        require(outputs.size == 1) { "The backbone should have one output, the embedding" }
        val oldTensors = model.tables(graph, SUBGRAPH_TENSORS)
        val embeddingShape = model.ints(oldTensors[outputs.single()], TENSOR_SHAPE)
        require(model.byteField(oldTensors[outputs.single()], TENSOR_TYPE) == FLOAT32) { "The embedding must be float32" }
        require(embeddingShape.lastOrNull() == head.dimensions) { "The head expects ${head.dimensions}-d embeddings, the backbone gives $embeddingShape" }
        val oldBuffers = model.tables(root, MODEL_BUFFERS)
        require(oldBuffers.none { model.field(it, BUFFER_OFFSET) != null }) { "Backbones with external buffers aren't supported" }
        val oldOpcodes = model.tables(root, MODEL_OPERATOR_CODES)
        val inputShape = model.ints(oldTensors[model.ints(graph, SUBGRAPH_INPUTS).single()], TENSOR_SHAPE)

        val b = FlatBufferBuilder(backbone.size + head.params.size * 4 + 4096)
        val blob = b.addBlob(backbone)
        fun old(position: Int) = b.inBlob(blob, position)

        // New weights, each in its own buffer
        val newBuffers = mutableListOf<Int>()
        fun buffer(values: FloatArray): Int {
            val data = b.createByteVector(values.toLittleEndianBytes())
            b.startTable()
            b.offsetField(BUFFER_DATA, data)
            newBuffers += b.endTable()
            return oldBuffers.size + newBuffers.size - 1
        }
        val emptyBuffer = 0 // TFLite's convention: buffer 0 is empty, for tensors computed at run time

        // New tensors, after the backbone's
        val newTensors = mutableListOf<Int>()
        fun tensor(name: String, shape: List<Int>, bufferIndex: Int): Int {
            val nameOffset = b.createString(name)
            val shapeOffset = b.createIntVector(shape)
            b.startTable()
            b.offsetField(TENSOR_SHAPE, shapeOffset)
            b.intField(TENSOR_BUFFER, bufferIndex)
            b.offsetField(TENSOR_NAME, nameOffset)
            b.byteField(TENSOR_TYPE, FLOAT32)
            newTensors += b.endTable()
            return oldTensors.size + newTensors.size - 1
        }

        // New operator codes, after the backbone's
        val newOpcodes = mutableListOf<Int>()
        fun opcode(builtin: Int): Int {
            b.startTable()
            b.byteField(OPCODE_DEPRECATED_BUILTIN, minOf(builtin, 127))
            b.intField(OPCODE_VERSION, 1)
            b.intField(OPCODE_BUILTIN, builtin)
            newOpcodes += b.endTable()
            return oldOpcodes.size + newOpcodes.size - 1
        }
        val l2Code = opcode(L2_NORMALIZATION)
        val fullyConnectedCode = opcode(FULLY_CONNECTED)
        val softmaxCode = opcode(SOFTMAX)

        val newOperators = mutableListOf<Int>()
        fun operator(code: Int, inputs: List<Int>, output: Int, optionsType: Int, options: Int) {
            val inputsOffset = b.createIntVector(inputs)
            val outputsOffset = b.createIntVector(listOf(output))
            b.startTable()
            b.intField(OPERATOR_OPCODE_INDEX, code)
            b.offsetField(OPERATOR_INPUTS, inputsOffset)
            b.offsetField(OPERATOR_OUTPUTS, outputsOffset)
            b.offsetField(OPERATOR_BUILTIN_OPTIONS, options)
            b.byteField(OPERATOR_BUILTIN_OPTIONS_TYPE, optionsType)
            newOperators += b.endTable()
        }
        fun fullyConnectedOptions(relu: Boolean): Int {
            b.startTable()
            b.byteField(0, if (relu) ACTIVATION_RELU else ACTIVATION_NONE)
            return b.endTable()
        }

        // The embedding is normalised, as the trainer does; the first fully connected layer flattens it
        val batch = 1
        val normalisedTensor = tensor("herblens/normalized", embeddingShape, emptyBuffer)
        b.startTable(); b.byteField(0, ACTIVATION_NONE); val l2Options = b.endTable()
        val l2Input = outputs.single()
        operator(l2Code, listOf(l2Input), normalisedTensor, OPTIONS_L2_NORM, l2Options)

        var features = normalisedTensor
        val p = head.params
        if (head.hidden > 0) {
            val weights = tensor("herblens/hidden/weights", listOf(head.hidden, head.dimensions), buffer(p.copyOfRange(0, head.hiddenBiasOffset)))
            val bias = tensor("herblens/hidden/bias", listOf(head.hidden), buffer(p.copyOfRange(head.hiddenBiasOffset, head.outputOffset)))
            val hiddenOut = tensor("herblens/hidden", listOf(batch, head.hidden), emptyBuffer)
            operator(fullyConnectedCode, listOf(features, weights, bias), hiddenOut, OPTIONS_FULLY_CONNECTED, fullyConnectedOptions(relu = true))
            features = hiddenOut
        }
        val outWeights = tensor("herblens/output/weights", listOf(head.classes, head.features), buffer(p.copyOfRange(head.outputOffset, head.outputBiasOffset)))
        val outBias = tensor("herblens/output/bias", listOf(head.classes), buffer(p.copyOfRange(head.outputBiasOffset, p.size)))
        val logits = tensor("herblens/logits", listOf(batch, head.classes), emptyBuffer)
        operator(fullyConnectedCode, listOf(features, outWeights, outBias), logits, OPTIONS_FULLY_CONNECTED, fullyConnectedOptions(relu = false))
        val probabilities = tensor("probabilities", listOf(batch, head.classes), emptyBuffer)
        b.startTable(); b.floatField(0, 1f); val softmaxOptions = b.endTable()
        operator(softmaxCode, listOf(logits), probabilities, OPTIONS_SOFTMAX, softmaxOptions)

        // Metadata, in a buffer of its own, named as the TFLite tools expect
        val metadataBytes = ModelMetadata.build(name, description, author, version, inputShape.getOrElse(1) { 224 }, LABELS_FILE)
        val metadataData = b.createByteVector(metadataBytes)
        b.startTable(); b.offsetField(BUFFER_DATA, metadataData); newBuffers += b.endTable()
        val metadataBuffer = oldBuffers.size + newBuffers.size - 1
        val metadataName = b.createString("TFLITE_METADATA")
        b.startTable(); b.offsetField(0, metadataName); b.intField(1, metadataBuffer); val metadataEntry = b.endTable()
        val metadataVector = b.createOffsetVector(listOf(metadataEntry))

        // The subgraph: the backbone's tensors and operators followed by the new ones
        val tensorsVector = b.createOffsetVector(oldTensors.map { old(it) } + newTensors)
        val operatorsVector = b.createOffsetVector(model.tables(graph, SUBGRAPH_OPERATORS).map { old(it) } + newOperators)
        val inputsVector = b.createIntVector(model.ints(graph, SUBGRAPH_INPUTS))
        val outputsVector = b.createIntVector(listOf(probabilities))
        val graphName = b.createString("main")
        b.startTable()
        b.offsetField(SUBGRAPH_TENSORS, tensorsVector)
        b.offsetField(SUBGRAPH_INPUTS, inputsVector)
        b.offsetField(SUBGRAPH_OUTPUTS, outputsVector)
        b.offsetField(SUBGRAPH_OPERATORS, operatorsVector)
        b.offsetField(SUBGRAPH_NAME, graphName)
        val newGraph = b.endTable()

        val opcodesVector = b.createOffsetVector(oldOpcodes.map { old(it) } + newOpcodes)
        val subgraphsVector = b.createOffsetVector(listOf(newGraph))
        val buffersVector = b.createOffsetVector(oldBuffers.map { old(it) } + newBuffers)
        val descriptionOffset = b.createString(name)
        b.startTable()
        b.intField(MODEL_VERSION, model.intField(root, MODEL_VERSION, 3))
        b.offsetField(MODEL_OPERATOR_CODES, opcodesVector)
        b.offsetField(MODEL_SUBGRAPHS, subgraphsVector)
        b.offsetField(MODEL_DESCRIPTION, descriptionOffset)
        b.offsetField(MODEL_BUFFERS, buffersVector)
        b.offsetField(MODEL_METADATA, metadataVector)
        val flatBuffer = b.finish(b.endTable(), "TFL3")
        return StoredZip.append(flatBuffer, mapOf(LABELS_FILE to labels.joinToString("\n", postfix = "\n").encodeToByteArray()) + extraFiles)
    }

    /** A file packed in a model by [export] (labels.txt or one of its extra files), or null. */
    fun packedFile(model: ByteArray, name: String): ByteArray? = StoredZip.read(model, name)

    /** The class names packed in a model (this app's exports, or any with TFLite metadata labels), or null. */
    fun labels(model: ByteArray): List<String>? =
        StoredZip.read(model, LABELS_FILE)?.decodeToString()?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }

    // Field ids and enum values from the TFLite schema (tensorflow/compiler/mlir/lite/schema/schema.fbs)
    private const val MODEL_VERSION = 0
    private const val MODEL_OPERATOR_CODES = 1
    private const val MODEL_SUBGRAPHS = 2
    private const val MODEL_DESCRIPTION = 3
    private const val MODEL_BUFFERS = 4
    private const val MODEL_METADATA = 6
    internal const val SUBGRAPH_TENSORS = 0
    internal const val SUBGRAPH_INPUTS = 1
    internal const val SUBGRAPH_OUTPUTS = 2
    internal const val SUBGRAPH_OPERATORS = 3
    private const val SUBGRAPH_NAME = 4
    internal const val TENSOR_SHAPE = 0
    internal const val TENSOR_TYPE = 1
    private const val TENSOR_BUFFER = 2
    internal const val TENSOR_NAME = 3
    private const val OPERATOR_OPCODE_INDEX = 0
    private const val OPERATOR_INPUTS = 1
    private const val OPERATOR_OUTPUTS = 2
    private const val OPERATOR_BUILTIN_OPTIONS_TYPE = 3
    private const val OPERATOR_BUILTIN_OPTIONS = 4
    private const val OPCODE_DEPRECATED_BUILTIN = 0
    private const val OPCODE_VERSION = 2
    internal const val OPCODE_BUILTIN = 3
    private const val BUFFER_DATA = 0
    private const val BUFFER_OFFSET = 1
    private const val FLOAT32 = 0
    internal const val FULLY_CONNECTED = 9
    internal const val L2_NORMALIZATION = 11
    internal const val SOFTMAX = 25
    private const val OPTIONS_FULLY_CONNECTED = 8
    private const val OPTIONS_SOFTMAX = 9
    private const val OPTIONS_L2_NORM = 12
    private const val ACTIVATION_NONE = 0
    private const val ACTIVATION_RELU = 1
}
