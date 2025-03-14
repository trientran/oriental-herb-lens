package com.uri.lee.dl.herbdetails.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.SetOptions
import com.uri.lee.dl.REVIEW_PATH_NAME
import com.uri.lee.dl.authUI
import com.uri.lee.dl.clock
import com.uri.lee.dl.herbCollection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.concurrent.CancellationException

class AddReviewViewModel(herbId: Long) : ViewModel() {

    class MyViewModelFactory(private val herbId: Long) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AddReviewViewModel::class.java)) {
                return AddReviewViewModel(herbId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private val stateFlow = MutableStateFlow(AddReviewState(herbId = herbId))

    fun state(): Flow<AddReviewState> = stateFlow

    val state: AddReviewState get() = stateFlow.value

    init {
        viewModelScope.launch { stateFlow.collect { Timber.d(it.toString()) } }
        viewModelScope.launch { setState { copy(review = AddReviewState.Review(uid = authUI.auth.uid!!)) } }
    }

    fun setCondition(condition: String) {
        Timber.d("setConditions")
        viewModelScope.launch { setState { copy(review = review?.copy(condition = condition)) } }
    }

    fun setEffectiveness(effectiveness: Float) {
        Timber.d("setEffectiveness")
        viewModelScope.launch { setState { copy(review = review?.copy(effectiveness = effectiveness)) } }
    }

    fun setEaseOfUse(easyOfUse: Float) {
        Timber.d("setEaseOfUse")
        viewModelScope.launch { setState { copy(review = review?.copy(easyOfUse = easyOfUse)) } }
    }

    fun setComment(comment: String) {
        Timber.d("setComment")
        viewModelScope.launch { setState { copy(review = review?.copy(comment = comment)) } }
    }

    fun setPatientName(patientName: String) {
        Timber.d("setPatientName")
        viewModelScope.launch { setState { copy(review = review?.copy(patientName = patientName)) } }
    }

    fun setAge(age: Int) {
        Timber.d("setAge")
        viewModelScope.launch { setState { copy(review = review?.copy(age = age)) } }
    }

    fun submit() {
        Timber.d("submit")
        viewModelScope.launch {
            setState { copy(isSubmitting = true) }
            try {
                state.review?.let {
                    requireNotNull(it.age)
                    requireNotNull(it.condition)
                    requireNotNull(it.effectiveness)
                    requireNotNull(it.comment)
                    requireNotNull(it.easyOfUse)
                    requireNotNull(it.patientName)
                    requireNotNull(it.uid)

                    val instant = clock.instant().toEpochMilli()
                    setState { copy(review = review?.copy(instant = instant)) }
                    herbCollection
                        .document(state.herbId.toString())
                        .set(
                            mapOf(REVIEW_PATH_NAME to mapOf(instant.toString() to state.review)),
                            SetOptions.merge()
                        )
                        .await()
                    setState { copy(isSubmitting = false, isSubmissionComplete = true) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e)
                setState { copy(error = AddReviewState.Error(e), isSubmitting = false, isSubmissionComplete = false) }
            }
        }
    }

    private inline fun setState(copiedState: AddReviewState.() -> AddReviewState) = stateFlow.update(copiedState)
}

data class AddReviewState(
    val herbId: Long,
    val review: Review? = null,
    val isSubmitting: Boolean = false,
    val isSubmissionComplete: Boolean = false,
    val error: Error? = null,
) {
    data class Review(
        val uid: String = "",
        val instant: Long? = null,
        val patientName: String = "",
        val age: Int? = null,
        val comment: String = "",
        val condition: String = "",
        val easyOfUse: Float? = null,
        val effectiveness: Float? = null,
    )

    data class Error(val exception: Exception)
}
