package com.uri.lee.dl.data.firebase

/** Firestore collection and field names. Don't change: they must match existing data and rules. */
internal object FirestorePaths {
    const val HERBS = "herbs"
    const val USERS = "users"
    const val CONFIG = "config"
    const val MOBILE_CONFIG_DOC = "mobile"

    const val USER_FAVORITES = "favorite"
    const val USER_HISTORY = "history"
    const val USER_IS_ADMIN = "isAdmin"

    const val HERB_IMAGES = "images"
    const val HERB_VI_NAME = "viName"
    const val HERB_LATIN_NAME = "latinName"
}
