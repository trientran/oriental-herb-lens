package com.uri.lee.dl.shared.research

import com.uri.lee.dl.core.training.ResourceMonitor
import com.uri.lee.dl.domain.training.AppFiles
import com.uri.lee.dl.domain.training.Dataset

/** Keeps a run going with the screen off or the app in the background, as far as the platform allows. */
interface ResearchBackground {
    /** A run starts; [onExpired] is called if the system ends the background time (the run is stopped, to resume later). */
    fun start(title: String, onExpired: () -> Unit) {}
    fun progress(done: Int, total: Int, text: String) {}
    fun stop() {}
}

/**
 * What each platform provides for research runs (plan Phase 7): device details and readings,
 * storage, dataset pickers, saving the results archive and running in the background.
 */
class ResearchPlatform(
    /** e.g. "Xiaomi Redmi Note 12 (Android 14, SM6225)". */
    val device: String,
    /** "android", "ios" or "web". */
    val platform: String,
    val monitor: ResourceMonitor,
    /** Where a run keeps its progress: the app's files, under research/. */
    val files: AppFiles,
    /** Lets the user choose a folder with one subfolder of photos per class; null if cancelled. */
    val pickDatasetFolder: suspend () -> Dataset?,
    /** The same from a .zip, where the platform can open one. */
    val pickDatasetZip: (suspend () -> Dataset?)? = null,
    /**
     * Datasets copied into the app's own folder (adb, devicectl, Finder), one folder each in
     * datasets/: no picker needed, which suits long runs prepared from a computer.
     */
    val appDatasets: suspend () -> List<Dataset> = { emptyList() },
    /** Hands the results archive to the user (save dialog, share sheet or download). */
    val saveArchive: suspend (fileName: String, bytes: ByteArray) -> Unit,
    /** Keeps the screen on during a run. */
    val keepAwake: (Boolean) -> Unit,
    val background: ResearchBackground = object : ResearchBackground {},
    /** Shown on the screen: what happens if the screen goes off or the app is left. */
    val backgroundNote: String = "",
)
