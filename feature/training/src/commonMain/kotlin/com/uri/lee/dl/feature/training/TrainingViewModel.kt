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
import com.uri.lee.dl.domain.analytics.Analytics
import com.uri.lee.dl.domain.analytics.AnalyticsEvent
import com.uri.lee.dl.domain.analytics.NoAnalytics
import com.uri.lee.dl.domain.repository.AuthRepository
import com.uri.lee.dl.domain.repository.SettingsRepository
import com.uri.lee.dl.domain.sharing.CommunityModel
import com.uri.lee.dl.domain.sharing.CommunityModelRepository
import com.uri.lee.dl.domain.sharing.ModelReportReason
import com.uri.lee.dl.domain.sharing.SharingProblem
import com.uri.lee.dl.domain.sharing.SharingRules
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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

    /** Models others shared, and one of them. */
    data object Community : TrainingScreen
    data class CommunityModel(val id: String) : TrainingScreen
}

/** Something running that the screen shows progress for. */
sealed interface TrainingWork {
    data object Downloading : TrainingWork
    data class Reading(val done: Int, val total: Int) : TrainingWork
    data class Learning(val epoch: Int, val maxEpochs: Int) : TrainingWork
    data object Saving : TrainingWork
    data object Uploading : TrainingWork
}

enum class TrainingError { READ_PHOTOS, DOWNLOAD, TRAIN, NO_LABELS, IMPORT, TOO_FEW_SPECIES, OPEN_MODEL, SHARE, COMMUNITY }

/** What sharing a model with everyone needs from the user first. */
sealed interface SharingStep {
    data object SignIn : SharingStep
    data object Terms : SharingStep

    /** Ready: share, and say whether to offer it to Hugging Face too. */
    data object Confirm : SharingStep
    data class Problem(val problem: SharingProblem) : SharingStep
}

/** Something done, said once. */
enum class TrainingMessage { SHARED, SHARED_HUGGING_FACE, ADDED, REPORTED, HIDDEN, REMOVED }

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
    /** A picked photo being identified instead of the camera, until the user goes back to the camera. */
    val photo: String? = null,
    val error: TrainingError? = null,
    /** Models everyone shared; null until loaded, which happens only while those screens are open. */
    val community: List<CommunityModel>? = null,
    /** The signed-in user (their own shared models can be removed); null when signed out. */
    val userId: String? = null,
    val sharing: SharingStep? = null,
    val message: TrainingMessage? = null,
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
    data class DeleteModel(val id: String) : TrainingAction
    data class Rename(val id: String, val name: String) : TrainingAction
    data class ImportModel(val fileName: String, val bytes: ByteArray) : TrainingAction
    data class ClassifyPhoto(val photo: LocalImage) : TrainingAction
    data object BackToCamera : TrainingAction
    data object DismissError : TrainingAction

    /** Shares the trained model on screen with everyone (after sign-in, the terms and the name checks). */
    data object ShareWithEveryone : TrainingAction

    /** The same for a model in the list, without opening it. */
    data class ShareModel(val id: String) : TrainingAction
    data object AcceptSharingTerms : TrainingAction
    data class ConfirmShare(val offerToHuggingFace: Boolean) : TrainingAction
    data object DismissSharing : TrainingAction
    data class AddCommunityModel(val id: String) : TrainingAction
    data class ReportCommunityModel(val id: String, val reason: ModelReportReason) : TrainingAction
    data class HideSharer(val uploaderId: String) : TrainingAction
    data class RemoveCommunityModel(val id: String) : TrainingAction
    data object DismissMessage : TrainingAction
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
    private val community: CommunityModelRepository,
    private val auth: AuthRepository,
    private val settings: SettingsRepository,
    private val analytics: Analytics = NoAnalytics,
) : MviViewModel<TrainingState, TrainingAction>(TrainingState()) {

    private val backStack = mutableListOf<TrainingScreen>()
    private var job: Job? = null
    private val embedderLock = Mutex()
    private var embedder: Pair<String, ImageEmbedder>? = null
    private var classifier: Pair<String, ImageEmbedder>? = null
    private var communityJob: Job? = null

    init {
        refresh()
        auth.observeUserId()
            .onEach { setState { copy(userId = it) } }
            .catch { log.w(it) { "Sign-in state unavailable" } }
            .launchIn(viewModelScope)
    }

    override fun onAction(action: TrainingAction) {
        when (action) {
            is TrainingAction.Open -> {
                if (action.screen is TrainingScreen.Use) {
                    val tried = currentState.model?.takeIf { it.id == action.screen.id } ?: currentState.models?.firstOrNull { it.id == action.screen.id }
                    tried?.let { analytics.log(AnalyticsEvent.ModelTried(it.trainedClasses.size)) }
                }
                open(action.screen)
            }
            TrainingAction.Back -> back()
            is TrainingAction.CreateModel -> create(action.name, action.quality, null)
            is TrainingAction.ImportDataset -> create(action.name, action.quality, action.dataset)
            is TrainingAction.AddSpecies -> addSpecies(action.name)
            is TrainingAction.AddPhotos -> launchWork { addPhotos(action.species, action.photos) }
            TrainingAction.Train -> train()
            TrainingAction.StopTraining -> job?.cancel()
            is TrainingAction.SaveSettings -> saveSettings(action.settings)
            TrainingAction.Delete -> delete()
            is TrainingAction.DeleteModel -> viewModelScope.launch {
                store.delete(action.id)
                if (currentState.model?.id == action.id) setState { copy(model = null) }
                refresh()
            }
            is TrainingAction.Rename -> rename(action.id, action.name)
            is TrainingAction.ImportModel -> importModel(action.fileName, action.bytes)
            is TrainingAction.ClassifyPhoto -> viewModelScope.launch {
                // The camera stops updating the results while a photo is shown
                setState { copy(photo = action.photo.uri, predictions = emptyList()) }
                val image = reader.read(action.photo)?.image
                if (image == null) setState { copy(photo = null, error = TrainingError.READ_PHOTOS) } else classify(image, fromCamera = false)
            }
            TrainingAction.BackToCamera -> setState { copy(photo = null, predictions = emptyList()) }
            TrainingAction.DismissError -> setState { copy(error = null) }
            TrainingAction.ShareWithEveryone -> shareWithEveryone(termsAccepted = false)
            is TrainingAction.ShareModel -> viewModelScope.launch {
                if (currentState.model?.id != action.id) load(action.id)
                shareWithEveryone(termsAccepted = false)
            }
            TrainingAction.AcceptSharingTerms -> {
                viewModelScope.launch { runCatching { settings.acceptSharingTerms() } }
                shareWithEveryone(termsAccepted = true)
            }
            is TrainingAction.ConfirmShare -> upload(action.offerToHuggingFace)
            TrainingAction.DismissSharing -> setState { copy(sharing = null) }
            is TrainingAction.AddCommunityModel -> addCommunityModel(action.id)
            is TrainingAction.ReportCommunityModel -> reportCommunityModel(action.id, action.reason)
            is TrainingAction.HideSharer -> viewModelScope.launch {
                runCatching { community.hideUploader(action.uploaderId) }
                back()
                setState { copy(message = TrainingMessage.HIDDEN) }
            }
            is TrainingAction.RemoveCommunityModel -> {
                val shared = sharedModel(action.id) ?: return
                launchWork(TrainingError.COMMUNITY) {
                    community.remove(shared)
                    // The model it came from can be shared again
                    store.list().filter { it.sharedId == shared.id }.forEach { store.save(it.copy(sharedId = null)) }
                    back()
                    setState { copy(message = TrainingMessage.REMOVED) }
                }
            }
            TrainingAction.DismissMessage -> setState { copy(message = null) }
        }
    }

    private fun sharedModel(id: String) = currentState.community?.firstOrNull { it.id == id }

    private fun shareWithEveryone(termsAccepted: Boolean) {
        val model = currentState.model ?: return
        if (currentState.userId == null) {
            setState { copy(sharing = SharingStep.SignIn) }
            return
        }
        viewModelScope.launch {
            if (!termsAccepted && !runCatching { settings.sharingTermsAccepted.first() }.getOrDefault(false)) {
                setState { copy(sharing = SharingStep.Terms) }
                return@launch
            }
            val file = store.tflite(model.id)
            if (file == null) {
                setState { copy(sharing = null, error = TrainingError.SHARE) }
                return@launch
            }
            SharingRules.problem(model.name, model.trainedClasses, file.size)?.let {
                setState { copy(sharing = SharingStep.Problem(it)) }
                return@launch
            }
            setState { copy(sharing = SharingStep.Confirm) }
        }
    }

    private fun upload(offerToHuggingFace: Boolean) {
        val model = currentState.model ?: return
        setState { copy(sharing = null) }
        launchWork(TrainingError.SHARE) {
            val file = store.tflite(model.id) ?: error("No model file")
            setState { copy(work = TrainingWork.Uploading) }
            // Trained here, so the file carries what's needed to go on learning
            val shared = community.share(model.name, model.trainedClasses, model.backbone, trainable = true, file = file, offerToHuggingFace = offerToHuggingFace)
            val updated = model.copy(sharedId = shared.id)
            store.save(updated)
            analytics.log(AnalyticsEvent.ModelShared(model.trainedClasses.size, to = "community", huggingFace = offerToHuggingFace))
            setState {
                copy(
                    model = if (this.model?.id == model.id) updated else this.model,
                    message = if (offerToHuggingFace) TrainingMessage.SHARED_HUGGING_FACE else TrainingMessage.SHARED,
                )
            }
        }
    }

    private fun addCommunityModel(id: String) {
        val shared = sharedModel(id) ?: return
        launchWork(TrainingError.COMMUNITY) {
            setState { copy(work = TrainingWork.Downloading) }
            val bytes = community.download(shared)
            if (importFile("${shared.name}.tflite", bytes)) {
                analytics.log(AnalyticsEvent.CommunityModelAdded(shared.species.size, shared.trainable))
                backStack.clear()
                show(TrainingScreen.Models)
                setState { copy(message = TrainingMessage.ADDED) }
            }
        }
    }

    private fun reportCommunityModel(id: String, reason: ModelReportReason) {
        val shared = sharedModel(id) ?: return
        if (currentState.userId == null) {
            setState { copy(sharing = SharingStep.SignIn) }
            return
        }
        launchWork(TrainingError.COMMUNITY) {
            community.report(shared, reason)
            analytics.log(AnalyticsEvent.ModelReported(reason.name))
            back()
            setState { copy(message = TrainingMessage.REPORTED) }
        }
    }

    /** Listens to the shared models only while their screens are open: each listen reads the list. */
    private fun watchCommunity(open: Boolean) {
        if (open && communityJob == null) {
            communityJob = community.observe()
                .onEach { setState { copy(community = it) } }
                .catch {
                    log.w(it) { "Shared models unavailable" }
                    setState { copy(community = emptyList(), error = TrainingError.COMMUNITY) }
                }
                .launchIn(viewModelScope)
        } else if (!open) {
            communityJob?.cancel()
            communityJob = null
            setState { copy(community = null) }
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
        setState { copy(screen = screen, predictions = emptyList(), photo = null) }
        watchCommunity(screen is TrainingScreen.Community || screen is TrainingScreen.CommunityModel)
        if (screen is TrainingScreen.Community || screen is TrainingScreen.CommunityModel) return
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
            // Replay only adds to the last model; with nothing new (new settings, say) it trains afresh
            val previous = if (model.settings.update == UpdateMode.REPLAY && currentState.hasChanges) store.head(model.id) else null
            val maxEpochs = model.settings.expert.maxEpochs
            val result = withContext(Dispatchers.Default) {
                ModelTrainer.train(model, examples, previous) { epoch ->
                    setState { copy(work = TrainingWork.Learning(epoch.epoch, maxEpochs), history = history + epoch) }
                }
            }
            setState { copy(work = TrainingWork.Saving) }
            val trained = model.copy(trainedThrough = result.trainedThrough, trainedClasses = model.classes, report = result.report)
            val tflite = TfliteExport.export(
                backbones.read(model.backbone), result.head, model.classes, model.name, author = "Herb Lens",
                extraFiles = SharedModel.pack(model, result.head, examples),
            )
            store.saveTrained(trained, result.head, tflite)
            analytics.log(
                AnalyticsEvent.ModelTrained(
                    quality = model.settings.quality.name.lowercase(),
                    speciesCount = model.classes.size,
                    photoCount = model.photos,
                    update = if (previous != null) "add" else "full",
                ),
            )
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

    private fun rename(id: String, name: String) {
        val clean = name.trim().replace('\n', ' ')
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val model = store.load(id) ?: return@launch
            val renamed = model.copy(name = clean)
            store.save(renamed)
            if (currentState.model?.id == id) setState { copy(model = renamed) }
            refresh()
        }
    }

    private fun importModel(fileName: String, bytes: ByteArray) = launchWork { importFile(fileName, bytes) }

    /** Adds a model file to the user's models; false (with the error shown) when it isn't one. */
    private suspend fun importFile(fileName: String, bytes: ByteArray): Boolean {
        val shared = runCatching { SharedModel.unpack(bytes) }.getOrNull()
        if (shared != null) {
            importShared(fileName, bytes, shared)
            return true
        }
        val labels = runCatching { TfliteExport.labels(bytes) }.getOrNull()
        if (labels.isNullOrEmpty()) {
            setState { copy(error = TrainingError.NO_LABELS) }
            return false
        }
        val id = "i" + Random.nextLong(1, Long.MAX_VALUE).toString(36)
        val model = UserModel(id, fileName.substringBeforeLast('.'), labels, imported = true, trainedClasses = labels)
        setState { copy(work = TrainingWork.Saving) }
        store.saveImported(model, bytes)
        analytics.log(AnalyticsEvent.ModelImported(trainable = false, speciesCount = labels.size))
        val models = store.list()
        setState { copy(models = models) }
        return true
    }

    /** A model shared from Herb Lens: it can go on learning here, from its layers and replay sample. */
    private suspend fun importShared(fileName: String, bytes: ByteArray, shared: SharedModel.Unpacked) {
        val pack = shared.pack
        val quality = Quality.entries.firstOrNull { it.backbone == pack.backbone && !it.augment } ?: Quality.BALANCED
        val settings = TrainingSettings(quality = quality)
        val replay = SharedModel.renumbered(shared.replay, settings.expert)
        val id = "m" + Random.nextLong(1, Long.MAX_VALUE).toString(36)
        val model = UserModel(
            id = id,
            name = fileName.substringBeforeLast('.'),
            classes = pack.labels,
            settings = settings,
            trainedThrough = replay.maxOfOrNull { it.photo } ?: -1,
            trainedClasses = pack.labels,
            importedTrainable = true,
        )
        setState { copy(work = TrainingWork.Saving) }
        store.save(model)
        store.addExamples(id, replay)
        store.saveTrained(model, pack.head, bytes)
        analytics.log(AnalyticsEvent.ModelImported(trainable = true, speciesCount = model.classes.size))
        val models = store.list()
        setState { copy(models = models) }
    }

    /** What the model in use thinks [image] shows: a camera frame, unless a picked photo is on screen. */
    suspend fun classify(image: ClassifierImage, fromCamera: Boolean = true) {
        if (fromCamera && currentState.photo != null) return
        val model = currentState.model ?: return
        val runner = classifierFor(model) ?: return
        val probabilities = runCatching { runner.embed(image) }.getOrElse { e ->
            log.e(e) { "Couldn't classify" }
            return
        }
        val labels = model.trainedClasses
        val top = probabilities.indices.filter { it < labels.size }.sortedByDescending { probabilities[it] }.take(3)
        if (fromCamera && currentState.photo != null) return
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
        analytics.log(AnalyticsEvent.ModelShared(model.trainedClasses.size, to = "file"))
        val name = model.name.filter { it.isLetterOrDigit() || it == ' ' || it == '-' }.trim().replace(' ', '_').ifEmpty { "model" }
        return "$name.tflite" to bytes
    }

    /** [failure] is the error shown if [block] fails; by default, from the screen it ran on. */
    private fun launchWork(failure: TrainingError? = null, block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
        job = viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Training work failed" }
                setState { copy(error = failure ?: if (screen is TrainingScreen.Training) TrainingError.TRAIN else TrainingError.IMPORT) }
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
