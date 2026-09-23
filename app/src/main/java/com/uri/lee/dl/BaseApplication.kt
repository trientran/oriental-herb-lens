package com.uri.lee.dl

import android.app.Application
import com.bumptech.glide.Glide
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.google.android.libraries.places.api.Places
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import androidx.work.WorkManager
import com.uri.lee.dl.data.content.ContentSyncWorker
import com.uri.lee.dl.data.content.LegacyModelCleanup
import com.uri.lee.dl.di.appModules
import org.koin.androidx.workmanager.koin.workManagerFactory
import okhttp3.OkHttpClient
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import timber.log.Timber
import java.io.InputStream

class BaseApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())

        FirebaseApp.initializeApp(this)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            if (BuildConfig.DEBUG) DebugAppCheckProviderFactory.getInstance()
            else PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.INFO else Level.ERROR)
            androidContext(this@BaseApplication)
            workManagerFactory()
            modules(appModules)
        }
        get<LegacyModelCleanup>().run()
        ContentSyncWorker.enqueue(WorkManager.getInstance(this))

        Places.initializeWithNewPlacesApiEnabled(applicationContext, BuildConfig.MAP_PRODUCTS_API_KEY)
        Glide.get(this).registry.replace(
            GlideUrl::class.java, InputStream::class.java,
            OkHttpUrlLoader.Factory(get<OkHttpClient>())
        )
    }
}
