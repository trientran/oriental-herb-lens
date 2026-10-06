package com.uri.lee.dl

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.uri.lee.dl.core.ml.UriImage
import com.uri.lee.dl.core.training.ResourceMonitor
import com.uri.lee.dl.core.training.ResourceSample
import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.shared.research.Dataset
import com.uri.lee.dl.shared.research.ResearchPlatform
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android's side of research mode (plan Phase 7). Create it in onCreate: it registers activity
 * results for the folder, zip and save pickers.
 */
internal class AndroidResearch(private val activity: ComponentActivity) {

    private var folderResult: CompletableDeferred<Uri?>? = null
    private var zipResult: CompletableDeferred<Uri?>? = null
    private var saveResult: CompletableDeferred<Uri?>? = null

    private val folderPicker = activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { folderResult?.complete(it) }
    private val zipPicker = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { zipResult?.complete(it) }
    private val savePicker = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { saveResult?.complete(it) }

    fun platform() = ResearchPlatform(
        device = deviceName(),
        platform = "android",
        monitor = AndroidResourceMonitor(activity.applicationContext),
        // Copied by adb into the app's own folder (see docs/user-trained-models.md)
        backbones = {
            withContext(Dispatchers.IO) {
                val folder = File(requireNotNull(activity.getExternalFilesDir(null)) { "No app folder" }, "backbones")
                folder.listFiles { f -> f.extension == "tflite" }.orEmpty().sortedBy { it.name }.associate { it.nameWithoutExtension to it.path }
            }
        },
        readModel = { withContext(Dispatchers.IO) { File(it).readBytes() } },
        appDatasets = {
            withContext(Dispatchers.IO) {
                val folder = File(requireNotNull(activity.getExternalFilesDir(null)) { "No app folder" }, "datasets")
                folder.listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name }.map { dataset ->
                    val files = dataset.walkTopDown().filter { it.isFile }.map { it.relativeTo(dataset).path to UriImage(Uri.fromFile(it)) }.toList()
                    Dataset.fromPaths(dataset.name, files)
                }
            }
        },
        pickDatasetFolder = {
            // Starts in Download, where datasets copied to the phone usually are
            val download = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download")
            val tree = CompletableDeferred<Uri?>().also { folderResult = it }.also { folderPicker.launch(download) }.await()
            tree?.let { withContext(Dispatchers.IO) { readTree(it) } }
        },
        pickDatasetZip = {
            val zip = CompletableDeferred<Uri?>().also { zipResult = it }.also { zipPicker.launch(arrayOf("application/zip")) }.await()
            zip?.let { withContext(Dispatchers.IO) { unzip(it) } }
        },
        saveArchive = { name, bytes ->
            val target = CompletableDeferred<Uri?>().also { saveResult = it }.also { savePicker.launch(name) }.await()
            if (target != null) {
                withContext(Dispatchers.IO) {
                    requireNotNull(activity.contentResolver.openOutputStream(target)) { "Can't write $target" }.use { it.write(bytes) }
                }
            }
        },
        keepAwake = { on ->
            activity.runOnUiThread {
                if (on) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        },
    )

    private fun deviceName(): String {
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ", ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else ""
        return "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}$soc)"
    }

    /** Every file under a folder the user chose, with its path inside it. */
    private fun readTree(tree: Uri): Dataset {
        val resolver = activity.contentResolver
        val files = mutableListOf<Pair<String, LocalImage>>()
        fun walk(documentId: String, path: String) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
            val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
            resolver.query(children, columns, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val name = cursor.getString(1)
                    if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        walk(id, "$path$name/")
                    } else {
                        files += "$path$name" to UriImage(DocumentsContract.buildDocumentUriUsingTree(tree, id))
                    }
                }
            }
        }
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        walk(rootId, "")
        return Dataset.fromPaths(rootId.substringAfterLast(':').substringAfterLast('/').ifEmpty { "dataset" }, files)
    }

    /** Unpacks a zip into the app's cache (replacing any earlier one) and reads it like a folder. */
    private fun unzip(zip: Uri): Dataset {
        val folder = File(activity.cacheDir, "research-dataset").apply { deleteRecursively(); mkdirs() }
        val files = mutableListOf<Pair<String, LocalImage>>()
        requireNotNull(activity.contentResolver.openInputStream(zip)) { "Can't open $zip" }.use { input ->
            ZipInputStream(input.buffered()).use { entries ->
                generateSequence { entries.nextEntry }.filterNot { it.isDirectory }.forEach { entry ->
                    val target = File(folder, entry.name).canonicalFile
                    // A zip can name paths like "../x": keep everything inside the folder
                    if (!target.path.startsWith(folder.canonicalPath + File.separator)) return@forEach
                    target.parentFile?.mkdirs()
                    target.outputStream().use { entries.copyTo(it) }
                    files += entry.name to UriImage(Uri.fromFile(target))
                }
            }
        }
        val name = activity.contentResolver.query(zip, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.removeSuffix(".zip") ?: "dataset"
        return Dataset.fromPaths(name, files)
    }
}

/**
 * This app's memory (PSS), CPU time and the battery's charge counter, which falls as energy is
 * used: the difference over a training step estimates its energy (while not charging).
 */
private class AndroidResourceMonitor(private val context: Context) : ResourceMonitor {
    private val battery = context.getSystemService(BatteryManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)

    override fun sample(): ResourceSample {
        val memory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return ResourceSample(
            memoryBytes = memory.totalPss * 1024L,
            cpuMillis = Process.getElapsedCpuTime(),
            chargeMicroAmpHours = battery?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.takeIf { it > 0 && it != Long.MIN_VALUE },
            batteryPercent = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 },
            charging = plugged != 0,
            thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) thermalName(power?.currentThermalStatus) else null,
        )
    }

    private fun thermalName(status: Int?): String? = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "none"
        PowerManager.THERMAL_STATUS_LIGHT -> "light"
        PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
        else -> null
    }
}
