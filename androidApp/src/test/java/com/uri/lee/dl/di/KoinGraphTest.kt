package com.uri.lee.dl.di

import android.app.Application
import android.content.Context
import android.content.res.AssetManager
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkerParameters
import com.uri.lee.dl.core.firebase.AnalyticsClient
import com.uri.lee.dl.core.firebase.AuthClient
import com.uri.lee.dl.core.firebase.FirestoreClient
import com.uri.lee.dl.core.firebase.RemoteConfigClient
import com.uri.lee.dl.core.firebase.firebaseModule
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import okio.Path
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

/** Koin resolves at runtime; this fails the build instead when a definition's dependency is missing. */
@OptIn(KoinExperimentalAPI::class)
class KoinGraphTest {

    @Test
    fun `every definition can be constructed`() {
        module { includes(appModules - firebaseModule) }.verify(
            extraTypes = listOf(
                Context::class,
                Application::class,
                // Built inline inside definitions, not resolved from the graph
                CoroutineScope::class,
                Path::class,
                AssetManager::class,
                HttpClientEngine::class,
                // Supplied by WorkManager when it creates a worker
                WorkerParameters::class,
                // Supplied by Koin to every ViewModel
                SavedStateHandle::class,
                // firebaseModule wraps Firebase SDK objects whose internals aren't ours to check
                AnalyticsClient::class,
                AuthClient::class,
                FirestoreClient::class,
                RemoteConfigClient::class,
                // Plain values passed inline (version code, sort language, URLs)
                Long::class,
                Boolean::class,
                String::class,
            ),
        )
    }
}
