package com.uri.lee.dl.data.content

import android.content.Context
import com.uri.lee.dl.MODEL_PREFS
import java.io.File

/**
 * Versions before content sync downloaded the model from Firebase Storage into the cache dir and
 * tracked it in the `model_prefs` SharedPreferences. Neither is read any more; remove both.
 */
class LegacyModelCleanup(private val context: Context) {
    fun run() {
        File(context.cacheDir, LEGACY_MODEL_FILE_NAME).delete()
        context.deleteSharedPreferences(MODEL_PREFS)
    }

    companion object {
        const val LEGACY_MODEL_FILE_NAME = "my_remote_model.tflite"
    }
}
