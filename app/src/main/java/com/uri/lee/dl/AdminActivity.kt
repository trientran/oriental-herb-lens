package com.uri.lee.dl

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.uri.lee.dl.databinding.ActivityAdminBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding

    private val adminViewModel: AdminViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.exportCsvFileView.setOnClickListener { adminViewModel.generateMultipleCSVs() }
        binding.syncRecognizedHerbListView.setOnClickListener { adminViewModel.syncRecognizedHerbs() }
        binding.syncToBeRecognizedHerbListView.setOnClickListener { adminViewModel.syncToBeRecognizedHerbs() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                adminViewModel.state()
                    .mapNotNull { it.fileUri }
                    .onEach {
                        val sendIntent = Intent()
                        sendIntent.action = Intent.ACTION_SEND
                        sendIntent.putExtra(Intent.EXTRA_STREAM, it)
                        sendIntent.type = "application/zip"
                        startActivity(Intent.createChooser(sendIntent, "SHARE"))
                    }
                    .launchIn(this)

                adminViewModel.state()
                    .map { it.isSubmitting }
                    .onEach { binding.progressBar.isVisible = it }
                    .launchIn(this)
            }
        }
    }
}
