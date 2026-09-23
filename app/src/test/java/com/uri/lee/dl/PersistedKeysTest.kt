package com.uri.lee.dl

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Pins every key the app persists today. The Phase 1 move to a single DataStore must migrate
 * exactly these keys, otherwise existing users silently lose their settings.
 */
class PersistedKeysTest {

    @Test
    fun `DataStore file and keys`() {
        assertEquals("SETTINGS", SETTINGS)
        assertEquals("IS_OBJECTS_MODE", IS_OBJECTS_MODE_SINGLE_IMAGE.name)
        assertEquals("CONFIDENCE_LEVEL", CONFIDENCE_LEVEL.name)
    }

    @Test
    fun `model SharedPreferences file and keys`() {
        assertEquals("model_prefs", MODEL_PREFS)
        assertEquals("model_file_path", DOWNLOADED_MODEL_FILE_PATH)
        assertEquals("model_url", MODEL_URL)
    }

    @Test
    fun `default SharedPreferences keys used by the settings screen and camera`() {
        val expected = mapOf(
            "pref_key_rear_camera_preview_size" to "rcpvs",
            "pref_key_rear_camera_picture_size" to "rcpts",
            "pref_key_object_detector_enable_multiple_objects" to "odemo",
            "pref_key_enable_auto_search" to "pkeas",
            "pref_key_confirmation_time_in_manual_search" to "ctims",
            "pref_key_confirmation_time_in_auto_search" to "ctias",
        )

        assertEquals(expected, prefKeyStrings())
    }

    /** Reads `pref_key_*` values straight from strings.xml; unit tests have no Android resources. */
    private fun prefKeyStrings(): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/values/strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length)
            .map { nodes.item(it) }
            .map { it.attributes.getNamedItem("name").nodeValue to it.textContent }
            .filter { (name, _) -> name.startsWith("pref_key_") }
            .toMap()
    }
}
