package com.uri.lee.dl.images

import android.content.ClipData
import android.content.Context
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
import com.uri.lee.dl.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import timber.log.Timber

@ExperimentalCoroutinesApi
class ImagesViewModel : ViewModel() {
    // Backing property to avoid state updates from other classes
    private val _recognitionList = MutableStateFlow<MutableList<Recognition>>(mutableListOf())

    // The UI collects from this StateFlow to get its state updates
    val recognitionList: StateFlow<MutableList<Recognition>> get() = this._recognitionList

    fun addData(recognition: Recognition) {
        val currentList = _recognitionList.value.toMutableList()
        currentList.add(recognition)
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

                val labeler = ImageLabeling.getClient(options)
                processImages(context, clipData, labeler)
                    .catch { Timber.e(it.message ?: "Some error") }
                    .onEach { addData(it) }
                    .flowOn(defaultDispatcher)
                    .launchIn(viewModelScope)
            }
    }

    private fun processImages(
        context: Context,
        clipData: ClipData,
        labeler: ImageLabeler
    ) = callbackFlow {

        var recognition: Recognition

        for (i in 0 until clipData.itemCount) {
            val selectedImageUri: Uri = clipData.getItemAt(i).uri
            val bitmap = selectedImageUri.toScaledBitmap(context, 224, 224) ?: return@callbackFlow
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            labeler.process(inputImage)
                .addOnSuccessListener { results ->
                    recognition = try {
                        Recognition(
                            label = results[0].text + " " + results[0].index,
                            confidence = results[0].confidence,
                            imageUri = selectedImageUri
                        )
                    } catch (e: IndexOutOfBoundsException) {
                        Recognition(
                            label = context.getString(R.string.no_result),
                            confidence = 0f,
                            imageUri = selectedImageUri
                        )
                    }
                    trySend(recognition)
                }
                .addOnFailureListener { Timber.e(it.message ?: "Some error") }
        }
        awaitClose { labeler.close() }
    }
}
