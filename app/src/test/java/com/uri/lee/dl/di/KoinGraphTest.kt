package com.uri.lee.dl.di

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

/** Koin resolves at runtime; this fails the build instead when a definition's dependency is missing. */
@OptIn(KoinExperimentalAPI::class)
class KoinGraphTest {

    @Test
    fun `every definition can be constructed`() {
        module { includes(appModules) }.verify(
            extraTypes = listOf(
                Context::class,
                Application::class,
                // Built inline inside definitions, not resolved from the graph
                CoroutineScope::class,
            ),
        )
    }
}
