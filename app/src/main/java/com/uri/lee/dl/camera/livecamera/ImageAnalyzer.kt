package com.uri.lee.dl.camera.livecamera

import android.content.Context
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.common.model.CustomRemoteModel
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.linkfirebase.FirebaseModelSource
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.uri.lee.dl.LOCAL_TFLITE_MODEL_NAME
import com.uri.lee.dl.REMOTE_TFLITE_MODEL_NAME
import com.uri.lee.dl.herbdetails.tempherbs.sciList70
import com.uri.lee.dl.herbdetails.tempherbs.viList70
import com.uri.lee.dl.labeling.Herb
import timber.log.Timber

class ImageAnalyzer(
    private val context: Context,
    private val confidence: Float = 0.5f,
    private val maxResultsDisplayed: Int = 1,
    private val recognitionListener: (herbs: List<Herb>) -> Unit,
) : ImageAnalysis.Analyzer {

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val inputImage = imageProxy.image?.let {
            InputImage.fromMediaImage(it, imageProxy.imageInfo.rotationDegrees)
        }
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
                    .setMaxResultCount(maxResultsDisplayed)
                    .build()

                val labeler = ImageLabeling.getClient(options)
                inputImage?.let {
                    processImage(labeler, it, imageProxy)
                }
            }
    }

    private val defaultLabeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    private fun processImage(
        labeler: ImageLabeler,
        inputImage: InputImage,
        imageProxy: ImageProxy
    ) {
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
                    val recognitionList = mutableListOf<Herb>()
                    labeler.process(inputImage)
                        .addOnSuccessListener { it ->
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
                            // Return the result
                            recognitionListener(recognitionList)
                            // Close the image,this tells CameraX to feed the next image to the analyzer
                            imageProxy.close()
                        }
                        .addOnFailureListener {
                            Timber.e(it.message ?: "Some error")
                        }
                } else {
                    recognitionListener(emptyList())
                }
            }
            .addOnFailureListener { Timber.e(it.message ?: "Some error") }
    }
}