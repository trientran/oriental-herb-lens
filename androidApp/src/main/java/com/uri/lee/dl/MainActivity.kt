package com.uri.lee.dl

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
