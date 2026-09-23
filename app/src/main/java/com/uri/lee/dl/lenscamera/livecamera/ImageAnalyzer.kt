package com.uri.lee.dl.lenscamera.livecamera

import android.content.Context
import android.os.Bundle
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.uri.lee.dl.getHerbModel
import com.uri.lee.dl.labeling.Herb
import timber.log.Timber
import com.uri.lee.dl.labeling.toHerbs
import com.uri.lee.dl.labeling.toRawLabel

class ImageAnalyzer(
    private val confidence: Float,
    private val maxResultsDisplayed: Int = 1,
    val recognizedLatinHerbs: Bundle? = null, // herbId, latin name
    val recognizedViHerbs: Bundle? = null, // HerbId, viet name
    private val context: Context,
    private val recognitionListener: (herbs: List<Herb>) -> Unit,
) : ImageAnalysis.Analyzer {
    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val inputImage = imageProxy.image?.let { InputImage.fromMediaImage(it, imageProxy.imageInfo.rotationDegrees) }
        getHerbModel(context) {
            val options = it.setConfidenceThreshold(confidence).setMaxResultCount(maxResultsDisplayed).build()
            val labeler = ImageLabeling.getClient(options)
            inputImage?.let { image -> processImage(labeler, image, imageProxy) }
        }
    }

    private fun processImage(labeler: ImageLabeler, inputImage: InputImage, imageProxy: ImageProxy) {
        labeler.process(inputImage)
            .addOnSuccessListener {
                val herbList = if (it.isEmpty()) {
                    listOf(Herb())
                } else {
                    it.take(maxResultsDisplayed).map { label -> label.toRawLabel() }.toHerbs(
                        latinNameOf = { id -> recognizedLatinHerbs!!.getString(id) },
                        viNameOf = { id -> recognizedViHerbs!!.getString(id) },
                    )
                }
                // Return the result
                recognitionListener.invoke(herbList)
                // Close the image,this tells CameraX to feed the next image to the analyzer
                imageProxy.close()
            }
            .addOnFailureListener {
                Timber.e(it.message ?: "Some error")
            }
    }
}