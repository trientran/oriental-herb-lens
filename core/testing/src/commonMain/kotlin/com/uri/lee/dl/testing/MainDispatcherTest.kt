package com.uri.lee.dl.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/**
 * Base for ViewModel tests on every platform: `viewModelScope` runs on [testDispatcher].
 * Create ViewModels inside tests (or lazily), not in property initialisers, which run first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class MainDispatcherTest(val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()) {
    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()
}
