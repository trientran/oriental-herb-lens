package com.uri.lee.dl.shared

import com.uri.lee.dl.domain.notification.UploadNotifier
import kotlinx.browser.window
import org.w3c.dom.events.Event

/**
 * The page shows how an upload ends, so nothing is notified; closing the tab while photos are
 * still uploading asks first, as that would stop them.
 */
internal class WebUploadNotifier : UploadNotifier {
    private var uploads = 0
    private val confirmLeaving: (Event) -> Unit = { it.preventDefault() }

    override fun uploadStarted() {
        if (uploads++ == 0) window.addEventListener("beforeunload", confirmLeaving)
    }

    override fun uploadFinished(speciesId: Long, speciesName: String?, uploaded: Int, failed: Int) {
        if (uploads > 0 && --uploads == 0) window.removeEventListener("beforeunload", confirmLeaving)
    }
}
