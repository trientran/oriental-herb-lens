package com.uri.lee.dl

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import com.uri.lee.dl.shared.App

/** Hosts the Compose app. Start it with [HERB_ID] to show a species, e.g. from a notification. */
class MainActivity : ComponentActivity() {

    private val openHerbId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        val actions = AndroidPlatform(this).actions()
        // Announcements sent from the Firebase console need this on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && savedInstanceState == null) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { App(actions = actions, openHerbId = openHerbId.value) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }


    private fun handle(intent: Intent) {
        intent.getLongExtra(HERB_ID, -1).takeIf { it >= 0 }?.let { openHerbId.value = it }
    }
}
