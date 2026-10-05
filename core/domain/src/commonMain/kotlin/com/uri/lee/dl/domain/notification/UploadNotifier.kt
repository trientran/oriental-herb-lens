package com.uri.lee.dl.domain.notification

/**
 * Tells the user how a photo upload ended, with a local notification, when they've left the app
 * meanwhile (uploads keep going in the background). In the app, the screen already shows it.
 */
fun interface UploadNotifier {
    /** An upload began; iOS uses it to ask for time to finish it in the background. */
    fun uploadStarted() {}

    /** [uploaded] is 0 when nothing could be uploaded. */
    fun uploadFinished(speciesId: Long, speciesName: String?, uploaded: Int, failed: Int)
}
