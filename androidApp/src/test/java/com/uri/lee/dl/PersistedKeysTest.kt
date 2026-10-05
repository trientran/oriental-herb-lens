package com.uri.lee.dl

import com.uri.lee.dl.data.content.LegacyCleanup
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the names of what older versions stored, which the clean-up must still find. DataStore
 * files and keys are pinned in core:data (DataStoreSettingsRepositoryTest).
 */
class PersistedKeysTest {

    @Test
    fun `legacy model download files`() {
        assertEquals("model_prefs", LegacyCleanup.MODEL_PREFS)
        assertEquals("my_remote_model.tflite", LegacyCleanup.LEGACY_MODEL_FILE_NAME)
    }

    @Test
    fun `Camera1 settings keys`() {
        assertEquals(setOf("rcpvs", "rcpts", "odemo", "pkeas", "ctims", "ctias"), LegacyCleanup.CAMERA_SETTINGS)
    }
}
