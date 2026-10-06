package com.uri.lee.dl.feature.training

import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.training.EpochStats
import com.uri.lee.dl.core.training.TfliteExport
import com.uri.lee.dl.core.ui.MviViewModel
import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.ml.ImageCropper
import com.uri.lee.dl.domain.ml.ImageEmbedder
import com.uri.lee.dl.domain.ml.ImageEmbedderLoader
import com.uri.lee.dl.domain.ml.PhotoReader
import com.uri.lee.dl.domain.ml.Region
import com.uri.lee.dl.domain.training.Backbones
import com.uri.lee.dl.domain.training.Dataset
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface TrainingScreen {
    data object Models : TrainingScreen
    data object NewModel : TrainingScreen
    data class Edit(val id: String) : TrainingScreen
    data class Training(val id: String) : TrainingScreen
    data class Result(val id: String) : TrainingScreen
    data class Settings(val id: String) : TrainingScreen
    data class Use(val id: String) : TrainingScreen
}

/** Something running that the screen shows progress for. */
sealed interface TrainingWork {
    data object Downloading : TrainingWork
    data class Reading(val done: Int, val total: Int) : TrainingWork
    data class Learning(val epoch: Int, val maxEpochs: Int) : TrainingWork
    data object Saving : TrainingWork
}

enum class TrainingError { READ_PHOTOS, DOWNLOAD, TRAIN, NO_LABELS, IMPORT, TOO_FEW_SPECIES, OPEN_MODEL }

data class TrainingState(
    val screen: TrainingScreen = TrainingScreen.Models,
    /** Null until loaded. */
    val models: List<UserModel>? = null,
    /** The model the screen is about (edit, result, settings, use). */
    val model: UserModel? = null,
    /** The highest photo number of [model], to tell whether it changed since training. */
    val lastPhoto: Int = -1,
    /** Photos added in this session, by species, to show as thumbnails. */
    val thumbnails: Map<String, List<String>> = emptyMap(),
    val work: TrainingWork? = null,
    /** The training running now, epoch by epoch. */
    val history: List<EpochStats> = emptyList(),
    /** What the model in use sees: species and probability, most likely first. */
    val predictions: List<Pair<String, Float>> = emptyList(),
    val error: TrainingError? = null,
) {
    val hasChanges: Boolean get() = model?.hasChanges(lastPhoto) == true
}

sealed interface TrainingAction {
    data class Open(val screen: TrainingScreen) : TrainingAction
    data object Back : TrainingAction
    data class CreateModel(val name: String, val quality: Quality) : TrainingAction
    data class ImportDataset(val name: String, val quality: Quality, val dataset: Dataset) : TrainingAction
    data class AddSpecies(val name: String) : TrainingAction
    data class AddPhotos(val species: String, val photos: List<LocalImage>) : TrainingAction
    data object Train : TrainingAction
    data object StopTraining : TrainingAction
    data class SaveSettings(val settings: TrainingSettings) : TrainingAction
    data object Delete : TrainingAction
    data class ImportModel(val fileName: String, val bytes: ByteArray) : TrainingAction
    data class ClassifyPhoto(val photo: LocalImage) : TrainingAction
    data object DismissError : TrainingAction
}

/**
 * Your own models (plan Phase 7): collect or import photos of each species, train on the device,
 * see how well it does, use it, and share it as a standalone .tflite. Models from others (.tflite
 * with labels inside) can be imported and used.
 */
class TrainingViewModel(
    private val store: UserModelStore,
    private val backbones: Backbones,
    private val reader: PhotoReader,
    private val embedders: ImageEmbedderLoader,
    private val cropper: ImageCropper,
) : MviViewModel<TrainingState, TrainingAction>(TrainingState()) {

    private val backStack = mutableListOf<TrainingScreen>()
    private var job: Job? = null
    private val embedderLock = Mutex()
    private var embedder: Pair<String, ImageEmbedder>? = null
    private var classifier: Pair<String, ImageEmbedder>? = null

    init {
        refresh()
    }

    override fun onAction(action: TrainingAction) {
        when (action) {
            is TrainingAction.Open -> open(action.screen)
            TrainingAction.Back -> back()
            is TrainingAction.CreateModel -> create(action.name, action.quality, null)
            is TrainingAction.ImportDataset -> create(action.name, action.quality, action.dataset)
            is TrainingAction.AddSpecies -> addSpecies(action.name)
            is TrainingAction.AddPhotos -> launchWork { addPhotos(action.species, action.photos) }
            TrainingAction.Train -> train()
            TrainingAction.StopTraining -> job?.cancel()
            is TrainingAction.SaveSettings -> saveSettings(action.settings)
            TrainingAction.Delete -> delete()
            is TrainingAction.ImportModel -> importModel(action.fileName, action.bytes)
            is TrainingAction.ClassifyPhoto -> viewModelScope.launch {
                reader.read(action.photo)?.image?.let { classify(it) } ?: setState { copy(error = TrainingError.READ_PHOTOS) }
            }
            TrainingAction.DismissError -> setState { copy(error = null) }
        }
    }

    private fun refresh() = viewModelScope.launch {
        val models = store.list()
        setState { copy(models = models) }
    }

    private fun open(screen: TrainingScreen) {
        backStack += currentState.screen
        show(screen)
    }

    private fun back() {
        // While training, only Stop leaves the screen
        if (currentState.work != null && currentState.screen is TrainingScreen.Training) return
        show(backStack.removeLastOrNull() ?: TrainingScreen.Models)
    }

    private fun show(screen: TrainingScreen) {
        val id = when (screen) {
            is TrainingScreen.Edit -> screen.id
            is TrainingScreen.Training -> screen.id
            is TrainingScreen.Result -> screen.id
            is TrainingScreen.Settings -> screen.id
            is TrainingScreen.Use -> screen.id
            else -> null
        }
        setState { copy(screen = screen, predictions = if (screen is TrainingScreen.Use) predictions else emptyList()) }
        if (id == null) {
            refresh()
        } else if (currentState.model?.id != id) {
            viewModelScope.launch { load(id) }
        }
    }

    private suspend fun load(id: String) {
        val model = store.load(id) ?: return
        val last = store.examples(id).maxOfOrNull { it.photo } ?: -1
        setState { copy(model = model, lastPhoto = last, thumbnails = if (this.model?.id == id) thumbnails else emptyMap()) }
    }

    private fun create(name: String, quality: Quality, dataset: Dataset?) {
        val id = "m" + Random.nextLong(1, Long.MAX_VALUE).toString(36)
        val classes = dataset?.classes.orEmpty()
        val model = UserModel(id, name.trim().ifEmpty { "Model" }, classes, settings = TrainingSettings(quality = quality))
        launchWork {
            store.save(model)
            setState { copy(model = model, lastPhoto = -1, thumbnails = emptyMap()) }
            backStack.clear()
            backStack += TrainingScreen.Models
            setState { copy(screen = TrainingScreen.Edit(id)) }
            if (dataset != null) {
                val photos = dataset.images
                embed(model, photos.map { it.className to it.image })
            }
        }
    }

    private fun addSpecies(name: String) {
        val model = currentState.model ?: return
        val clean = name.trim()
        if (clean.isEmpty() || clean in model.classes) return
        val updated = model.copy(classes = model.classes + clean)
        setState { copy(model = updated) }
        viewModelScope.launch { store.save(updated) }
    }

    private suspend fun addPhotos(species: String, photos: List<LocalImage>) {
        val model = currentState.model ?: return
        setState { copy(thumbnails = thumbnails + (species to (thumbnails[species].orEmpty() + photos.map { it.uri }))) }
        embed(model, photos.map { species to it })
    }

    /** Embeds photos with the model's backbone (and crops of them, for the best quality) and stores them. */
    private suspend fun embed(model: UserModel, photos: List<Pair<String, LocalImage>>) {
        val embedder = embedderFor(model.backbone) ?: return
        var next = currentState.lastPhoto + 1
        var unreadable = 0
        val examples = mutableListOf<StoredExample>()
        photos.forEachIndexed { i, (species, photo) ->
            setState { copy(work = TrainingWork.Reading(i, photos.size)) }
            val image = reader.read(photo)?.image
            val classIndex = model.classes.indexOf(species)
            if (image == null || classIndex < 0) {
                unreadable++
                return@forEachIndexed
            }
            val photoNumber = next++
            examples += StoredExample(classIndex, photoNumber, original = true, embedding = embedder.embed(image))
            if (model.settings.quality.augment) {
                for (region in CROPS) cropper.crop(image, region)?.let { examples += StoredExample(classIndex, photoNumber, false, embedder.embed(it)) }
            }
            // Saved in batches, so an interruption keeps most of the work
            if (examples.size >= SAVE_EVERY) {
                store.addExamples(model.id, examples.toList())
                examples.clear()
            }
        }
        store.addExamples(model.id, examples)
        load(model.id)
        if (unreadable > 0) setState { copy(error = TrainingError.READ_PHOTOS) }
    }

    private suspend fun embedderFor(backbone: String): ImageEmbedder? = embedderLock.withLock {
        embedder?.takeIf { it.first == backbone }?.second ?: run {
            val location = runCatching { backbones.location(backbone) { setState { copy(work = TrainingWork.Downloading) } } }
                .getOrElse { e ->
                    log.e(e) { "Backbone $backbone unavailable" }
                    setState { copy(error = TrainingError.DOWNLOAD) }
                    return@withLock null
                }
            embedder?.second?.close()
            embedders.load(location).also { embedder = backbone to it }
        }
    }

    private fun train() {
        val model = currentState.model ?: return
        if (model.classes.count { (model.photoCounts[it] ?: 0) > 0 } < 2) {
            setState { copy(error = TrainingError.TOO_FEW_SPECIES) }
            return
        }
        open(TrainingScreen.Training(model.id))
        setState { copy(history = emptyList()) }
        launchWork {
            val examples = store.examples(model.id)
            val previous = if (model.settings.update == UpdateMode.REPLAY) store.head(model.id) else null
            val maxEpochs = model.settings.expert.maxEpochs
            val result = withContext(Dispatchers.Default) {
                ModelTrainer.train(model, examples, previous) { epoch ->
                    setState { copy(work = TrainingWork.Learning(epoch.epoch, maxEpochs), history = history + epoch) }
                }
            }
            setState { copy(work = TrainingWork.Saving) }
            val trained = model.copy(trainedThrough = result.trainedThrough, trainedClasses = model.classes, report = result.report)
            val tflite = TfliteExport.export(backbones.read(model.backbone), result.head, model.classes, model.name, author = "Herb Lens")
            store.saveTrained(trained, result.head, tflite)
            classifier?.second?.close()
            classifier = null
            load(model.id)
            backStack.removeLastOrNull()
            setState { copy(screen = TrainingScreen.Result(model.id)) }
        }
    }

    private fun saveSettings(settings: TrainingSettings) {
        val model = currentState.model ?: return
        // A different backbone means different embeddings: only before any photos are in
        val quality = if (model.photos > 0 && settings.quality.backbone != model.backbone) model.settings.quality else settings.quality
        val updated = model.copy(settings = settings.copy(quality = quality))
        setState { copy(model = updated) }
        viewModelScope.launch { store.save(updated) }
        back()
    }

    private fun delete() {
        val model = currentState.model ?: return
        viewModelScope.launch {
            store.delete(model.id)
            backStack.clear()
            setState { copy(model = null) }
            show(TrainingScreen.Models)
        }
    }

    private fun importModel(fileName: String, bytes: ByteArray) {
        val labels = runCatching { TfliteExport.labels(bytes) }.getOrNull()
        if (labels.isNullOrEmpty()) {
            setState { copy(error = TrainingError.NO_LABELS) }
            return
        }
        val id = "i" + Random.nextLong(1, Long.MAX_VALUE).toString(36)
        val model = UserModel(id, fileName.substringBeforeLast('.'), labels, imported = true, trainedClasses = labels)
        launchWork {
            setState { copy(work = TrainingWork.Saving) }
            store.saveImported(model, bytes)
            val models = store.list()
            setState { copy(models = models) }
        }
    }

    /** What the model in use thinks [image] shows; also for live camera frames. */
    suspend fun classify(image: ClassifierImage) {
        val model = currentState.model ?: return
        val runner = classifierFor(model) ?: return
        val probabilities = runCatching { runner.embed(image) }.getOrElse { e ->
            log.e(e) { "Couldn't classify" }
            return
        }
        val labels = model.trainedClasses
        val top = probabilities.indices.filter { it < labels.size }.sortedByDescending { probabilities[it] }.take(3)
        setState { copy(predictions = top.map { labels[it] to probabilities[it] }) }
    }

    private suspend fun classifierFor(model: UserModel): ImageEmbedder? = embedderLock.withLock {
        classifier?.takeIf { it.first == model.id }?.second ?: run {
            classifier?.second?.close()
            runCatching { embedders.load(store.tfliteLocation(model.id)) }.getOrElse { e ->
                log.e(e) { "Couldn't open ${model.id}" }
                setState { copy(error = TrainingError.OPEN_MODEL) }
                return@withLock null
            }.also { classifier = model.id to it }
        }
    }

    /** The model's .tflite and a file name for it, to share. */
    suspend fun export(): Pair<String, ByteArray>? {
        val model = currentState.model ?: return null
        val bytes = store.tflite(model.id) ?: return null
        val name = model.name.filter { it.isLetterOrDigit() || it == ' ' || it == '-' }.trim().replace(' ', '_').ifEmpty { "model" }
        return "$name.tflite" to bytes
    }

    private fun launchWork(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
        job = viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Training work failed" }
                setState { copy(error = if (screen is TrainingScreen.Training) TrainingError.TRAIN else TrainingError.IMPORT) }
            } finally {
                setState { copy(work = null) }
                // Stopped or failed while training: back to the model
                if (currentState.screen is TrainingScreen.Training) back()
            }
        }
    }

    override fun onCleared() {
        embedder?.second?.close()
        classifier?.second?.close()
    }

    private companion object {
        val log = Logger.withTag("Training")
        const val SAVE_EVERY = 100

        /** Extra views of each photo for the best quality: a slightly and a closely zoomed middle. */
        val CROPS = listOf(Region(0.1f, 0.1f, 0.9f, 0.9f), Region(0.2f, 0.2f, 0.8f, 0.8f))
    }
}
