package com.uri.lee.dl

import com.google.firebase.firestore.toObject
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import com.google.firebase.Firebase
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant
import java.util.concurrent.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Legacy admin tools, kept until an offline export script replaces the image-URL dataset export
// (decision D5). They keep their own Firestore handles so nothing else depends on them.
private val herbCollection get() = Firebase.firestore.collection("herbs")
private val configCollection get() = Firebase.firestore.collection("config")

class AdminViewModel(application: Application) : AndroidViewModel(application) {

    private val application = getApplication<BaseApplication>()

    private val stateFlow = MutableStateFlow(AdminState())

    /** Emits the current state. */
    fun state(): Flow<AdminState> = stateFlow

    /** Retrieves the current state. */
    val state: AdminState get() = stateFlow.value

    fun generateMultipleCSVs() {
        Timber.d("loadImageUrls")
        viewModelScope.launch(Dispatchers.IO) {
            setState { copy(fileUri = null, isSubmitting = true) }
            herbCollection.whereNotEqualTo("images", null).get().await().let { documents ->
                val path = application.getExternalFilesDir(null)

                // zip file declaration
                val zipFile = File(path, "herbs_${Instant.now().epochSecond}.zip")
                val zipOutputStream = ZipOutputStream(FileOutputStream(zipFile))

                // herb list file declaration
                val herbListCsv = File(path, "herbs.csv")
                herbListCsv.delete()
                herbListCsv.createNewFile()

                // in every document/herb, we write to each CSV a full list of images for that specific herb
                // then, we add that file immediately into the zip file
                //
                for (document in documents) {
                    document.toObject<FireStoreHerb>().let { fireStoreHerb ->
                        Timber.d(fireStoreHerb.toString())

                        // create a single CSV to store all the images URL for this herb
                        val filename = "${fireStoreHerb.id}.csv"
                        val csv = File(path, filename)
                        csv.delete()
                        csv.createNewFile()
                        fireStoreHerb.images?.let {
                            val imagesCount = fireStoreHerb.images.count()
                            fireStoreHerb.images.onEachIndexed { index, entry ->
                                csv.appendText(entry.key)
                                if (index < imagesCount - 1) csv.appendText("\n")
                            }
                        }

                        // add this CSV into the zip file now
                        val fileInputStream = FileInputStream(csv)
                        val zipEntry = ZipEntry(csv.name)
                        zipOutputStream.putNextEntry(zipEntry)
                        val buffer = ByteArray(1024)
                        var length: Int
                        while (fileInputStream.read(buffer).also { length = it } > 0) {
                            zipOutputStream.write(buffer, 0, length)
                        }
                        zipOutputStream.closeEntry()
                        fileInputStream.close()

                        // also write to the herb list file this herb id and name
                        herbListCsv.appendText("${fireStoreHerb.id},${fireStoreHerb.viName}")
                        herbListCsv.appendText("\n")
                    }
                }
                // add herb list file into the zip file as well, and finally close the stream
                val fileInputStream = FileInputStream(herbListCsv)
                val zipEntry = ZipEntry(herbListCsv.name)
                zipOutputStream.putNextEntry(zipEntry)
                val buffer = ByteArray(1024)
                var length: Int
                while (fileInputStream.read(buffer).also { length = it } > 0) {
                    zipOutputStream.write(buffer, 0, length)
                }
                zipOutputStream.closeEntry()
                fileInputStream.close()
                zipOutputStream.close()

                // set file uri to the zip file uri
                setState {
                    copy(
                        fileUri = FileProvider.getUriForFile(
                            application,
                            "${application.packageName}.fileprovider",
                            zipFile
                        ),
                        isSubmitting = false,
                    )
                }
            }
        }
    }

    fun clearURIs() {
        viewModelScope.launch { setState { copy(fileUri = null, error = null) } }
    }

    fun syncToBeRecognizedHerbs() {
        viewModelScope.launch {
            setState { copy(isSubmitting = true) }
            try {
                herbCollection.get().await().let {
                    val latinMap = mutableMapOf<String, String>() // herbId, latin name
                    val viMap = mutableMapOf<String, String>() // herbId, viet name
                    it.documents.forEach { doc ->
                        doc.toObject<FireStoreHerb>()?.let { herb ->
                            latinMap[herb.id!!.toString()] = herb.latinName
                            viMap[herb.id.toString()] = herb.viName
                        }
                    }
                    configCollection
                        .document("mobile")
                        .update(
                            mapOf(
                                "toBeRecognizedLatinHerbs" to latinMap,
                                "toBeRecognizedViHerbs" to viMap,
                                "herbCount" to it.documents.count()
                            )
                        )
                        .await()
                    setState { copy(isSubmitting = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e)
                setState { copy(error = AdminState.Error(e), isSubmitting = false) }
            }
        }
    }

    fun syncRecognizedHerbs() {
        viewModelScope.launch {
            setState { copy(isSubmitting = true) }
            try {
                configCollection.document("mobile").get().await().toObject<FireStoreMobile>()?.let {
                    configCollection
                        .document("mobile")
                        .update(
                            mapOf(
                                "recognizedLatinHerbs" to it.toBeRecognizedLatinHerbs,
                                "recognizedViHerbs" to it.toBeRecognizedViHerbs,
                                "recognizedHerbsCount" to it.toBeRecognizedViHerbs.count()
                            )
                        )
                        .await()
                }
                setState { copy(isSubmitting = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e)
                setState { copy(error = AdminState.Error(e), isSubmitting = false) }
            }
        }
    }

    private inline fun setState(copiedState: AdminState.() -> AdminState) = stateFlow.update(copiedState)
}

data class AdminState(
    val fileUri: Uri? = null,
    val error: Error? = null,
    val isSubmitting: Boolean = false,
) {
    data class Error(val exception: Exception)
}
