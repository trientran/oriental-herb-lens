package com.uri.lee.dl

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey

// Persisted key names. Renaming any of these silently resets the user's setting.
const val SETTINGS = "SETTINGS"
val IS_OBJECTS_MODE_SINGLE_IMAGE = booleanPreferencesKey("IS_OBJECTS_MODE")
val CONFIDENCE_LEVEL = floatPreferencesKey("CONFIDENCE_LEVEL")
