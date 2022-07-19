package com.uri.lee.dl.image

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.uri.lee.dl.LOCAL_TFLITE_MODEL_NAME
import com.uri.lee.dl.REMOTE_TFLITE_MODEL_NAME
import com.uri.lee.dl.herbdetails.tempherbs.sciList70
import com.uri.lee.dl.herbdetails.tempherbs.viList70
import com.uri.lee.dl.ioDispatcher
import com.uri.lee.dl.labeling.Herb
import com.uri.lee.dl.labeling.HerbError
import com.uri.lee.dl.labeling.HerbError.LabelingError
import com.uri.lee.dl.labeling.HerbError.ObjectDetectionError
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException

class ImageViewModel : ViewModel() {
    // This is a LiveData field. Choosing this structure because the whole list tend to be updated
    // at once in ML and not individual elements. Updating this once for the entire list makes
    // sense.
    private val _imageUri = MutableLiveData<Uri>()
    val imageUri: LiveData<Uri> get() = _imageUri
    private val _recognitionList = MutableLiveData<MutableList<Herb>>(mutableListOf())
    val recognitionList: LiveData<MutableList<Herb>> get() = _recognitionList
    private val _objectList = MutableLiveData<MutableList<DetectedObject>>(mutableListOf())
    val objectList: LiveData<MutableList<DetectedObject>> get() = _objectList
    private val _error = MutableLiveData<HerbError>()
    val error: LiveData<HerbError> get() = _error

    private var detector: ObjectDetector? = null
    private var labeler: ImageLabeler? = null
    private val defaultLabeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    init {
        detector = ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects()
                .build()
        )
    }

    fun setImageUri(imageUri: Uri) {
        _imageUri.value = imageUri
    }

    fun setObjectList(objectList: MutableList<DetectedObject>) {
        _objectList.value = objectList
    }

    private fun setError(error: HerbError) {
        _error.value = error
    }

    override fun onCleared() {
        super.onCleared()
        try {
            detector?.close()
            labeler?.close()
        } catch (e: IOException) {
            Timber.e("Failed to close the detector!")
        }
    }

    fun inferImageLabels(
        context: Context,
        bitmap: Bitmap,
        confidence: Float = 0.0f,
        listener: (herbList: List<Herb>) -> Unit,
    ) {
        viewModelScope.launch(ioDispatcher) {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
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
                        .build()
                    labeler = ImageLabeling.getClient(options)
                    labelImage(inputImage, context) { listener.invoke(it) }
                }
        }
    }

    fun inferImageLabelsWithDefaultModel(bitmap: Bitmap) {
        viewModelScope.launch(ioDispatcher) {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            defaultLabeler.process(inputImage)
                .addOnSuccessListener { labels ->
                    labels.onEach {
                        Log.d("trien112", it.text)
//                        Plant
//                        Vegetable
//                        Petal
//                        Flower
//                        Garden
//                        Fruit
                    }
                }
                .addOnFailureListener { e ->
                    // Task failed with an exception
                    // ...
                }
        }
    }

    fun detectObject(image: InputImage, callback: (List<DetectedObject>) -> Unit) {
        detector!!.process(image)
            .addOnSuccessListener { objects -> callback.invoke(objects) }
            .addOnFailureListener {
                callback.invoke(mutableListOf())
                setError(ObjectDetectionError(it))
            }
    }

    private fun updateData(recognitions: MutableList<Herb>) {
        _recognitionList.value = recognitions
    }

    private fun labelImage(inputImage: InputImage, context: Context, listener: (herbList: List<Herb>) -> Unit) {
        defaultLabeler.process(inputImage)
            .addOnSuccessListener { labels ->
                val recognitionList = arrayListOf<Herb>()
                val couldBeHerb =
                    labels.any { listOf("Plant", "Vegetable", "Petal", "Flower", "Garden", "Fruit").contains(it.text) }
                if (couldBeHerb) {
                    labeler!!.process(inputImage)
                        .addOnSuccessListener {
                            val maxResultsDisplayed = 1 //if (it.first().confidence >= 0.5) 2 else 1
                            for (i in 0 until maxResultsDisplayed) {
                                val id = it[i].text.substringBefore(" ")
                                recognitionList.add(
                                    Herb(
                                        id = id,
                                        sciName = sciList70[id]!!,
                                        viName = viList70[id]!!,
                                        confident = it[i].confidence
                                    )
                                )
                            }
                            Timber.d(recognitionList.toString())
                            listener.invoke(recognitionList)
                        }
                        .addOnFailureListener { setError(LabelingError(it)) }
                } else {
                    listener.invoke(emptyList())
                }
            }
            .addOnFailureListener { setError(LabelingError(it)) }
    }
}
