package com.uri.lee.dl.image

import android.content.Context
import android.net.Uri
import android.widget.Toast
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
import com.uri.lee.dl.*
import com.uri.lee.dl.labeling.Herb
import com.uri.lee.dl.labeling.ImageSource
import kotlinx.coroutines.launch
import timber.log.Timber

class ImageViewModel : ViewModel() {
    // This is a LiveData field. Choosing this structure because the whole list tend to be updated
    // at once in ML and not individual elements. Updating this once for the entire list makes
    // sense.
    private val _imageUri = MutableLiveData<Uri>()
    val imageUri: LiveData<Uri> get() = _imageUri
    private val _recognitionList = MutableLiveData<MutableList<Herb>>(mutableListOf())
    val recognitionList: LiveData<MutableList<Herb>> get() = _recognitionList

    private fun updateData(recognitions: MutableList<Herb>) {
        _recognitionList.value = recognitions
    }

    fun updateImageUri(imageUri: Uri) {
        _imageUri.value = imageUri
    }

    fun inferImageLabels(
        context: Context,
        imageSource: ImageSource,
        confidence: Float = 0.0f,
    ) {
        viewModelScope.launch(ioDispatcher) {
            //todo change image size for future model
            val bitmap = when (imageSource) {
                //todo bitmap size based on model shape obtained from FB list of constants + datastore
                is ImageSource.Uri -> imageSource.uri.toScaledBitmap(context, 224, 224) ?: return@launch
                is ImageSource.Bitmap -> imageSource.bitmap
            }

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

                    val labeler = ImageLabeling.getClient(options)
                    labelImage(labeler, inputImage, context)
                }
        }
    }

    fun labelObject(
        detectedObject: DetectedObjectInfo,
        listener: (detectedObject: DetectedObjectInfo, herbList: List<Herb>) -> Unit
    ) {
        detectedObject.getBitmap()
        val herbList = ArrayList<Herb>()
        for (i in 0..7) {
            herbList.add(Herb(imageUrl = "", title = "Herb: $i", subtitle = "Herb code: $i"))
        }
        listener.invoke(detectedObject, herbList)
    }

    private fun labelImage(
        labeler: ImageLabeler,
        inputImage: InputImage,
        context: Context
    ) {

        labeler.process(inputImage)
            .addOnSuccessListener {
                val maxResultsDisplayed = if (it.first().confidence >= 0.5) 2 else 1
                val recognitionList = arrayListOf<Herb>()
                for (i in 0 until maxResultsDisplayed) {
                    try {
                        recognitionList.add(
                            Herb(
                                title = it[i].text.substringBefore(" "),
                                subtitle = it[i].text.substringAfter(" ")
                            )
                        )
                    } catch (e: IndexOutOfBoundsException) {
                        recognitionList.add(
                            Herb(
                                title = context.getString(R.string.no_result),
                                subtitle = ""
                            )
                        )
                    }
                }
                Timber.d(recognitionList.toString())
                updateData(recognitionList)
            }
            .addOnFailureListener { Toast.makeText(context, it.localizedMessage, Toast.LENGTH_SHORT).show() }
    }
}



