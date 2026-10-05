package com.uri.lee.dl.data.content

import android.content.Context
import java.io.File

/**
 * Removes what older versions stored and nothing reads any more:
 * - the model downloaded from Firebase Storage into the cache dir, tracked in `model_prefs`
 *   (replaced by content sync)
 * - the Camera1 screen's settings in the default SharedPreferences (replaced by the CameraX
 *   Identify screen)
 */
class LegacyCleanup(private val context: Context) {
    fun run() {
        File(context.cacheDir, LEGACY_MODEL_FILE_NAME).delete()
        context.deleteSharedPreferences(MODEL_PREFS)
        val defaults = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
        if (CAMERA_SETTINGS.any(defaults::contains)) {
            defaults.edit().apply { CAMERA_SETTINGS.forEach(::remove) }.apply()
        }
    }

    companion object {
        const val MODEL_PREFS = "model_prefs"
        const val LEGACY_MODEL_FILE_NAME = "my_remote_model.tflite"

        /** Keys the Camera1 settings screen saved: preview and picture size, auto search, confirmation times. */
        val CAMERA_SETTINGS = setOf("rcpvs", "rcpts", "odemo", "pkeas", "ctims", "ctias")
    }
}
