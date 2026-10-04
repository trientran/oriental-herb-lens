package com.uri.lee.dl.lenscamera

import com.uri.lee.dl.domain.model.ScanSettings
import com.uri.lee.dl.testing.fakes.FakeSettingsRepository
import com.uri.lee.dl.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CameraViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val settings = FakeSettingsRepository(ScanSettings(minConfidence = 0.35f))

    @Test
    fun `starts from the saved confidence`() {
        assertEquals(0.35f, CameraViewModel(settings).state.value.confidence)
    }

    @Test
    fun `a new confidence updates state and is saved`() = runTest {
        val viewModel = CameraViewModel(settings)

        viewModel.onAction(CameraAction.ConfidenceChanged(0.8f))

        assertEquals(0.8f, viewModel.state.value.confidence)
        assertEquals(0.8f, settings.scanSettings.value.minConfidence)
    }
}
