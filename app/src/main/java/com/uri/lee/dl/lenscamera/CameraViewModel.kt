package com.uri.lee.dl.lenscamera

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uri.lee.dl.BaseApplication
import com.uri.lee.dl.CONFIDENCE_LEVEL
import com.uri.lee.dl.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.CancellationException

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val application = getApplication<BaseApplication>()
    private val stateFlow = MutableStateFlow(CameraState())

    /** Emits the current state. */
    fun state(): Flow<CameraState> = stateFlow

    /** Retrieves the current state. */
    val state: CameraState get() = stateFlow.value

    init {
        viewModelScope.launch { stateFlow.collect { Timber.d(it.toString()) } }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            getConfidence()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private suspend fun getConfidence() {
        application.dataStore.data
            .map { settings -> settings[CONFIDENCE_LEVEL] ?: 0.7f }
            .take(1)
            .collect { confidence -> setState { copy(confidence = confidence) } }
    }

    fun setConfidence(confidence: Float) {
        Timber.d("setConfidence")
        viewModelScope.launch {
            setState { copy(confidence = confidence) }
            application.dataStore.edit { settings -> settings[CONFIDENCE_LEVEL] = confidence }
        }
    }

    private inline fun setState(copiedState: CameraState.() -> CameraState) = stateFlow.update(copiedState)
}

data class CameraState(val confidence: Float? = null)
