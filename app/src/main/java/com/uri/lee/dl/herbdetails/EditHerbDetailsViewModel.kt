package com.uri.lee.dl.herbdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.uri.lee.dl.herbCollection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.concurrent.CancellationException

class EditHerbDetailsViewModel(herbId: Long, fieldName: String, oldValue: String) : ViewModel() {

    class MyViewModelFactory(
        private val herbId: Long,
        private val fieldName: String,
        private val oldValue: String,
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(EditHerbDetailsViewModel::class.java)) {
                return EditHerbDetailsViewModel(herbId, fieldName, oldValue) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private val stateFlow = MutableStateFlow(
        EditHerbDetailsState(
            herbId = herbId,
            fieldName = fieldName,
            newValue = oldValue,
            oldValue = oldValue
        )
    )

    fun state(): Flow<EditHerbDetailsState> = stateFlow

    val state: EditHerbDetailsState get() = stateFlow.value

    init {
        viewModelScope.launch { stateFlow.collect { Timber.d(it.toString()) } }
    }

    fun setNewValue(newValue: String) {
        Timber.d("setNewValue")
        viewModelScope.launch { setState { copy(newValue = newValue) } }
    }

    fun update() {
        Timber.d("update")
        viewModelScope.launch {
            setState { copy(isSubmitting = true) }
            try {
                herbCollection.document(state.herbId.toString()).update(state.fieldName, state.newValue).await()
                setState { copy(isSubmitting = false, isUpdateComplete = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e)
                setState { copy(error = EditHerbDetailsState.Error(e), isSubmitting = false, isUpdateComplete = false) }
            }
        }
    }

    private inline fun setState(copiedState: EditHerbDetailsState.() -> EditHerbDetailsState) =
        stateFlow.update(copiedState)
}

data class EditHerbDetailsState(
    val herbId: Long,
    val fieldName: String,
    val oldValue: String,
    val newValue: String,
    val isSubmitting: Boolean = false,
    val isUpdateComplete: Boolean = false,
    val error: Error? = null,
) {
    data class Error(val exception: Exception)
}
