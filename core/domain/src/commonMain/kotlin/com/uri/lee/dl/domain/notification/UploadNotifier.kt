package com.uri.lee.dl.domain.notification

/**
 * Tells the user how a photo upload ended, with a local notification, when they've left the app
 * meanwhile (uploads keep going in the background). In the app, the screen already shows it.
 */
fun interface UploadNotifier {
    /** An upload began (photos or a shared model); iOS uses it to ask for time to finish it in the background. */
    fun uploadStarted() {}

    /** Sharing the model [modelName] ended; [shared] is false when it failed. Says so if the app isn't on screen. */
    fun modelShareFinished(modelName: String, shared: Boolean) {}

    /** [uploaded] is 0 when nothing could be uploaded. */
    fun uploadFinished(speciesId: Long, speciesName: String?, uploaded: Int, failed: Int)
}
