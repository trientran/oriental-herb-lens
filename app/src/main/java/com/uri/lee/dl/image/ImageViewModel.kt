package com.uri.lee.dl.image

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.uri.lee.dl.*
import com.uri.lee.dl.herbdetails.tempherbs.sciList70
import com.uri.lee.dl.herbdetails.tempherbs.viList70
import com.uri.lee.dl.labeling.Herb
import com.uri.lee.dl.labeling.HerbEvent
import com.uri.lee.dl.labeling.HerbEvent.ObjectDetectionError
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException


class ImageViewModel(app: Application) : AndroidViewModel(app) {
    // This is a LiveData field. Choosing this structure because the whole list tend to be updated
    // at once in ML and not individual elements. Updating this once for the entire list makes
    // sense.
    private val _imageUri = MutableLiveData<Uri>()
    val imageUri: LiveData<Uri> get() = _imageUri
    private val _event = MutableLiveData<HerbEvent?>()
    val error: LiveData<HerbEvent?> get() = _event
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading
    private val _isEntireImageMode = MutableLiveData<Boolean>()
    val isEntireImageMode: LiveData<Boolean> get() = _isEntireImageMode

    private var detector: ObjectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .build()
    )

    private var labeler: ImageLabeler? = null

    fun setImageUri(imageUri: Uri) {
        Timber.d("setImageUri")
        _imageUri.value = imageUri
    }

    private fun setLoading(boolean: Boolean) {
        _isLoading.value = boolean
    }

    private fun setEvent(event: HerbEvent?) {
        _event.value = event
    }

    override fun onCleared() {
        super.onCleared()
        try {
            detector.close()
            labeler?.close()
        } catch (e: IOException) {
            Timber.e("Failed to close the detector!")
        }
    }

    private suspend fun getBitmapFromFileUri(context: Context, imageUri: Uri, maxDimension: Int): Bitmap? = try {
        Utils.loadImage(context, imageUri, maxDimension)
    } catch (e: IOException) {
        setEvent(HerbEvent.BitmapError(e))
        Timber.e(e.message)
        null
    }

    fun inferEntireImageLabels(
        context: Context,
        uri: Uri,
        confidence: Float = 0.5f,
        listener: (herbList: List<Herb>) -> Unit,
    ) {
        Timber.d("inferEntireImageLabels")
        viewModelScope.launch {
            setEvent(null)
            setLoading(true)
            val bitmap = getBitmapFromFileUri(context, uri, MAX_IMAGE_DIMENSION_FOR_LABELING) ?: return@launch
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            // set the minimum confidence required:
            isModelDownloaded {
                val options = it.setConfidenceThreshold(confidence).build()
                labeler = ImageLabeling.getClient(options)
                labelImage(inputImage, isEntireImageMode = true) { herbs -> listener.invoke(herbs) }
            }
            setLoading(false)
        }
    }

    fun inferObjectsLabels(
        bitmap: Bitmap,
        confidence: Float = 0.5f,
        listener: (herbList: List<Herb>) -> Unit,
    ) {
        Timber.d("inferObjectsLabels")
        viewModelScope.launch {
            setEvent(null)
            setLoading(true)
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            // set the minimum confidence required:
            isModelDownloaded {
                val options = it.setConfidenceThreshold(confidence).build()
                labeler = ImageLabeling.getClient(options)
                labelImage(inputImage, isEntireImageMode = false) { herbs -> listener.invoke(herbs) }
            }
            setLoading(false)
        }
    }

    fun detectObject(context: Context, uri: Uri, callback: (originalBitmap: Bitmap, List<DetectedObject>) -> Unit) {
        Timber.d("detectObject")
        viewModelScope.launch {
            setLoading(true)
            setEvent(null)
            val bitmap = getBitmapFromFileUri(context, uri, MAX_IMAGE_DIMENSION_FOR_OBJECT_DETECTION) ?: return@launch
            detector.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { objects ->
                    if (objects.isEmpty()) setEvent(ObjectDetectionError(Exception())) else callback.invoke(
                        bitmap,
                        objects
                    )
                    setLoading(false)
                }
                .addOnFailureListener {
                    setLoading(false)
                    Timber.e(it.message)
                    setEvent(ObjectDetectionError(it))
                }
        }
    }

    private fun labelImage(
        inputImage: InputImage,
        isEntireImageMode: Boolean,
        listener: (herbList: List<Herb>) -> Unit
    ) {
        viewModelScope.launch {
            setLoading(true)
            labeler!!.process(inputImage)
                .addOnSuccessListener {
                    if (it.isEmpty()) {
                        if (isEntireImageMode) listener.invoke(emptyList())
                        return@addOnSuccessListener
                    }
                    val maxResultsDisplayed = it.size
                    val recognitionList = mutableListOf<Herb>()
                    for (i in 0 until maxResultsDisplayed) {
                        val id = it[i].text.substringBefore(" ")
                        recognitionList.add(
                            Herb(
                                id = id,
                                sciName = sciList70[id]!!,
                                viName = viList70[id]!!,
                                confidence = it[i].confidence
                            )
                        )
                    }
                    Timber.d(recognitionList.toString())
                    listener.invoke(recognitionList)
                }
                .addOnFailureListener {
                    Timber.e(it.message)
                }
            setLoading(false)
        }
    }

    fun setEntireImageMode(checked: Boolean, context: Context) {
        Timber.d("setEntireImageMode")
        _isEntireImageMode.value = checked
        viewModelScope.launch {
            context.dataStore.edit { settings -> settings[IS_ENTIRE_IMAGE_MODE_SINGLE_IMAGE] = checked }
        }
    }
}

class ExtraParamsViewModelFactory(
    private val application: Application,
) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ImageViewModel(application) as T
}
