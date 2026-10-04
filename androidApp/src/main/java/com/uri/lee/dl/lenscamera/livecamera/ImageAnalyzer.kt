package com.uri.lee.dl.lenscamera.livecamera

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.uri.lee.dl.data.ml.MlKitClassifierImage
import com.uri.lee.dl.domain.usecase.RecognizeHerbsUseCase
import com.uri.lee.dl.labeling.Herb
import com.uri.lee.dl.labeling.toHerbs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Labels camera frames. CameraX delivers the next frame only after the current one is closed, so
 * each frame is closed once labelling finishes, fails, or is cancelled.
 */
class ImageAnalyzer(
    private val scope: CoroutineScope,
    private val recognizeHerbs: RecognizeHerbsUseCase,
    private val confidence: Float,
    private val maxResults: Int = 1,
    private val onResult: (herbs: List<Herb>) -> Unit,
) : ImageAnalysis.Analyzer {

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: return imageProxy.close()
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scope.launch {
            try {
                val herbs = recognizeHerbs(MlKitClassifierImage(input), confidence, maxResults).toHerbs()
                // An empty placeholder row keeps the result area's height stable.
                onResult(herbs.ifEmpty { listOf(Herb()) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e)
            }
        }.invokeOnCompletion { imageProxy.close() }
    }
}
