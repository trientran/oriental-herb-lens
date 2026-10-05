package com.uri.lee.dl.core.ml

import android.content.Context
import co.touchlab.kermit.Logger
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.domain.moderation.PlantCheck
import kotlinx.coroutines.tasks.await

/** ML Kit's general labeller from Google Play services (its model isn't in the APK). */
internal class MlKitPlantCheck(private val context: Context) : PlantCheck {
    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(PLANT_LABEL_CONFIDENCE).build())
    }

    override suspend fun showsPlant(image: LocalImage): Boolean? {
        val bitmap = context.loadUprightBitmap((image as UriImage).contentUri, maxDimension = 640) ?: return null
        return try {
            labeler.process(InputImage.fromBitmap(bitmap, 0)).await().any { it.text in PLANT_LABELS }
        } catch (e: MlKitException) {
            if (e.errorCode != MlKitException.UNAVAILABLE) throw e
            // Play services is still downloading the model (just after install): let the photo
            // through rather than block sharing; reports remain the safety net
            Logger.withTag("PlantCheck").w(e) { "Labeller not ready" }
            true
        }
    }
}
