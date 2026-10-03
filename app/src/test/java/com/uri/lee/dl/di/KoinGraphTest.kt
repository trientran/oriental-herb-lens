package com.uri.lee.dl.di

import android.app.Application
import android.content.Context
import android.content.res.AssetManager
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify
import java.io.File

/** Koin resolves at runtime; this fails the build instead when a definition's dependency is missing. */
@OptIn(KoinExperimentalAPI::class)
class KoinGraphTest {

    @Test
    fun `every definition can be constructed`() {
        module { includes(appModules - sdkModule) }.verify(
            extraTypes = listOf(
                Context::class,
                Application::class,
                // Built inline inside definitions, not resolved from the graph
                CoroutineScope::class,
                File::class,
                AssetManager::class,
                // Supplied by WorkManager when it creates a worker
                WorkerParameters::class,
                // Supplied by Koin to every ViewModel
                SavedStateHandle::class,
                // sdkModule: third-party objects whose internals aren't ours to check
                OkHttpClient::class,
                HttpClient::class,
                FirebaseAuth::class,
                FirebaseFirestore::class,
                FirebaseRemoteConfig::class,
                // Plain values passed inline (version code, sort language)
                Long::class,
                Boolean::class,
                String::class,
            ),
        )
    }
}
