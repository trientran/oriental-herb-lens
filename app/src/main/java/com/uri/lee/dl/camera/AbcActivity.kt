package com.uri.lee.dl.camera

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.uri.lee.dl.R

class AbcActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_abc)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, AbcFragment())
                .commitNow()
        }
    }
}