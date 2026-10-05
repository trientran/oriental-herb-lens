package com.uri.lee.dl

import android.app.Application
import androidx.work.WorkManager
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.uri.lee.dl.data.content.ContentSyncWorker
import com.uri.lee.dl.data.content.LegacyCleanup
import com.uri.lee.dl.di.appModules
import com.uri.lee.dl.shared.runStartupTasks
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class BaseApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // All modules log through Kermit, which writes to Logcat
        Logger.setMinSeverity(if (BuildConfig.DEBUG) Severity.Verbose else Severity.Warn)

        FirebaseApp.initializeApp(this)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            if (BuildConfig.DEBUG) DebugAppCheckProviderFactory.getInstance()
            else PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        val koin = startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.INFO else Level.ERROR)
            androidContext(this@BaseApplication)
            workManagerFactory()
            modules(appModules)
        }.koin
        get<LegacyCleanup>().run()
        koin.runStartupTasks()
        ContentSyncWorker.enqueue(WorkManager.getInstance(this))
    }
}
