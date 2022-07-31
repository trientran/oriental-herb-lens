package com.uri.lee.dl.camera.livecamera

import android.content.Context
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.uri.lee.dl.labeling.Herb
import java.util.concurrent.ExecutorService

class CameraViewModel : ViewModel() {
    // This is a LiveData field. Choosing this structure because the whole list tend to be updated
    // at once in ML and not individual elements. Updating this once for the entire list makes
    // sense.

    private val _recognitionList = MutableLiveData<List<Herb>>(emptyList())
    val recognitionList: LiveData<List<Herb>> get() = _recognitionList

    private fun updateData(recognitions: List<Herb>) {
        _recognitionList.value = recognitions
    }

    fun analyzeImage(context: Context, cameraExecutor: ExecutorService): ImageAnalysis {

        return ImageAnalysis.Builder()
            // This sets the ideal size for the image to be analyse, CameraX will choose the
            // the most suitable resolution which may not be exactly the same or hold the same
            // aspect ratio
            .setTargetResolution(Size(224, 224))
            // How the Image Analyser should pipe in input, 1. every frame but drop no frame, or
            // 2. go to the latest frame and may drop some frame. The default is 2.
            // STRATEGY_KEEP_ONLY_LATEST. The following line is optional, kept here for clarity
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, ImageAnalyzer(context) { recognitionList ->
                    // updating the list of recognised objects
                    updateData(recognitionList)
                })
            }
    }
}