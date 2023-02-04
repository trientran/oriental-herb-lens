package com.uri.lee.dl

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.io.File
import java.util.concurrent.CancellationException

class AdminViewModel(application: Application) : AndroidViewModel(application) {

    private val application = getApplication<BaseApplication>()

    private val stateFlow = MutableStateFlow(AdminState())

    private lateinit var personalListListenerRegistration: ListenerRegistration

    /** Emits the current state. */
    fun state(): Flow<AdminState> = stateFlow

    /** Retrieves the current state. */
    val state: AdminState get() = stateFlow.value

    init {
        viewModelScope.launch { stateFlow.collect { Timber.d(it.toString()) } }
        try {
            liveDeletionRequestUpdate()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e)
            setState { copy(error = AdminState.Error(e)) }
        }
    }

    fun loadImageUrls() {
        Timber.d("loadImageUrls")
        viewModelScope.launch(ioDispatcher) {
            setState { copy(fileUri = null) }
            herbCollection.whereNotEqualTo("images", null).get().await().let { documents ->
                val filename = "imageUrls.csv"
                val path = application.getExternalFilesDir(null)
                val fileOut = File(path, filename)
                fileOut.delete()
                fileOut.createNewFile()
                for (document in documents) {
                    document.toObject<FireStoreHerb>().let { fireStoreHerb ->
                        Timber.d(fireStoreHerb.toString())
                        fireStoreHerb.images?.forEach {
                            fileOut.appendText("${it.key},${fireStoreHerb.id}")
                            fileOut.appendText("\n")
                            Timber.d(it.key + it.value)
                        }
                    }
                }
                fileOut.appendText("\nhjj")
                setState {
                    copy(
                        fileUri = FileProvider.getUriForFile(
                            application,
                            "${application.packageName}.fileprovider",
                            fileOut
                        )
                    )
                }
            }
        }
    }

    private fun liveDeletionRequestUpdate() {
//        personalListListenerRegistration = userCollection.document(authUI.auth.uid!!)
//            .addSnapshotListener { snapshot, e ->
//                viewModelScope.launch {
//                    if (e != null) {
//                        Timber.e(e)
//                        setState { copy(error = AdminState.Error(e)) }
//                        return@launch
//                    }
//                    if (snapshot != null && snapshot.exists()) {
//                        val historyHerbIds = (snapshot.get(USER_HISTORY_FIELD_NAME) as? List<*>)
//                            ?.reversed()
//                            ?.mapNotNull { it as? Long }
//                        historyHerbIds?.let { setState { copy(historyHerbIds = it) } }
//                        val favoriteHerbIds = (snapshot.get(USER_FAVORITE_FIELD_NAME) as? List<*>)
//                            ?.reversed()
//                            ?.mapNotNull { it as? Long }
//                        favoriteHerbIds?.let { setState { copy(favoriteHerbIds = it) } }
//                    }
//                }
//            }
    }

    override fun onCleared() {
        super.onCleared()
        if (this::personalListListenerRegistration.isInitialized) personalListListenerRegistration.remove()
    }

    private inline fun setState(copiedState: AdminState.() -> AdminState) = stateFlow.update(copiedState)

}

data class AdminState(
    val fileUri: Uri? = null,
    val error: Error? = null,
) {
    data class Error(val exception: Exception)
}
