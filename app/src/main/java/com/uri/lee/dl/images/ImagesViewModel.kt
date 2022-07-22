package com.uri.lee.dl.images

import android.content.ClipData
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.common.model.CustomRemoteModel
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.linkfirebase.FirebaseModelSource
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.uri.lee.dl.*
import com.uri.lee.dl.herbdetails.tempherbs.sciList70
import com.uri.lee.dl.herbdetails.tempherbs.viList70
import com.uri.lee.dl.labeling.Herb
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.io.IOException

@ExperimentalCoroutinesApi
class ImagesViewModel : ViewModel() {
    // Backing property to avoid state updates from other classes
    private val _recognitionList = MutableStateFlow<MutableList<Herb>>(mutableListOf())

    // The UI collects from this StateFlow to get its state updates
    val recognitionList: StateFlow<MutableList<Herb>> get() = this._recognitionList

    private lateinit var labeler: ImageLabeler
    private val defaultLabeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    private fun addData(herb: Herb) {
        val currentList = _recognitionList.value.toMutableList()
        currentList.add(herb)
        _recognitionList.value = currentList
    }

    fun clearAllData() {
        _recognitionList.value = mutableListOf()
    }

    fun inferImages(
        context: Context,
        clipData: ClipData,
        confidence: Float = 0.0f,
    ) {
        // set the minimum confidence required:
        val localModel = LocalModel.Builder().setAssetFilePath(LOCAL_TFLITE_MODEL_NAME).build()

        // Specify the name you assigned in the Firebase console.
        val remoteModel = CustomRemoteModel
            .Builder(FirebaseModelSource.Builder(REMOTE_TFLITE_MODEL_NAME).build())
            .build()

        RemoteModelManager.getInstance().isModelDownloaded(remoteModel)
            .addOnSuccessListener { isDownloaded ->
                val optionsBuilder =
                    if (isDownloaded) {
                        Timber.d("Remote model being used")
                        CustomImageLabelerOptions.Builder(remoteModel)
                    } else {
                        Timber.d("Local model being used")
                        CustomImageLabelerOptions.Builder(localModel)
                    }
                val options = optionsBuilder
                    .setConfidenceThreshold(confidence)
                    .setMaxResultCount(1)
                    .build()

                labeler = ImageLabeling.getClient(options)
                processImages(context, clipData)
                    .onEach { addData(it) }
                    .flowOn(defaultDispatcher)
                    .launchIn(viewModelScope)
            }
    }

    private suspend fun getBitmapFromFileUri(context: Context, imageUri: Uri): Bitmap? =
        // run blocking to make sure bitmap is ready for all others
        try {
            Utils.loadImage(context, imageUri, MAX_IMAGE_DIMENSION_FOR_OBJECT_DETECTION)
        } catch (e: IOException) {
            Timber.e(e.message)
            null
        }

    private fun processImages(
        context: Context,
        clipData: ClipData,
    ) = callbackFlow {
        for (i in 0 until clipData.itemCount) {
            val selectedImageUri: Uri = clipData.getItemAt(i).uri
            val bitmap = getBitmapFromFileUri(context, selectedImageUri)
            val inputImage = InputImage.fromBitmap(bitmap!!, 0)

            defaultLabeler.process(inputImage)
                .addOnSuccessListener { labels ->
                    val couldBeHerb =
                        labels.any {
                            listOf(
                                "Plant",
                                "Vegetable",
                                "Petal",
                                "Flower",
                                "Garden",
                                "Fruit"
                            ).contains(it.text)
                        }
                    if (couldBeHerb) {
                        labeler.process(inputImage)
                            .addOnSuccessListener { results ->
                                val id = results[0].text.substringBefore(" ")
                                trySend(
                                    Herb(
                                        id = id,
                                        sciName = sciList70[id]!!,
                                        viName = viList70[id]!!,
                                        confident = results[0].confidence,
                                        imageFileUri = selectedImageUri
                                    )
                                )
                            }
                            .addOnFailureListener { Timber.e(it.message ?: "Some error") }
                    } else {
                        trySend(Herb(imageFileUri = selectedImageUri))
                    }
                }
                .addOnFailureListener { Timber.e(it.message ?: "Some error") }
        }
        awaitClose { labeler.close() }
    }
}
