package com.uri.lee.dl.upload

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.uri.lee.dl.HERB_ID
import com.uri.lee.dl.MainActivity
import com.uri.lee.dl.R
import com.uri.lee.dl.Utils
import com.uri.lee.dl.databinding.ActivityImageUploadBinding
import com.uri.lee.dl.lensimages.ImagesBottomSheetDialog
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ImageUploadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageUploadBinding

    private lateinit var imageUploadAdapter: ImageUploadAdapter

    private val viewModel: ImageUploadViewModel by viewModels()

    private var resultLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { list -> viewModel.addImageUris(list) }

    private val mainScope = MainScope()
    private val channelId = "image_upload"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageUploadBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        intent.getStringExtra(HERB_ID)?.let { viewModel.setHerbId(it) }
        imageUploadAdapter = ImageUploadAdapter {
            val bottomSheet = FullSizeImageViewerDialog(it)
            bottomSheet.show(supportFragmentManager, "ModalBottomSheet")
        }

        val gridLayoutManager = GridLayoutManager(this, GridLayoutManager.VERTICAL)
        binding.recyclerView.layoutManager = gridLayoutManager
        binding.recyclerView.adapter = imageUploadAdapter

        binding.closeButton.setOnClickListener { finish() }
        binding.clearBtn.setOnClickListener { viewModel.clearAllData() }
        binding.instructionView.setOnClickListener {
            AlertDialog.Builder(it.context)
                .setMessage(getString(R.string.how_to_take_photos))
                .setCancelable(true)
                .setNeutralButton("OK") { dialog, _ -> dialog.dismiss() }
                .create().show()
        }

        binding.addImagesBtn.setOnClickListener {
            it.isEnabled = false
            resultLauncher.launch("image/*")
        }
        binding.pickPhotosView.setOnClickListener {
            it.isEnabled = false
            resultLauncher.launch("image/*")
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state()
                    .map { it.imageUris }
                    .distinctUntilChanged()
                    .onEach { uriList ->
                        imageUploadAdapter.submitList(uriList)
                        binding.uploadBtn.isVisible = uriList.isNotEmpty()
                        binding.uploadBtn.setOnClickListener {
                            viewModel.uploadSequentially()
                            Toast.makeText(
                                this@ImageUploadActivity,
                                getString(R.string.upload_in_progress_please),
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                        }
                    }
                    .launchIn(this)
            }
        }

        createNotificationChannel()
        mainScope.launch {
            viewModel.state()
                .map { it.isUploadComplete }
                .distinctUntilChanged()
                .onEach {
                    val notificationId = 1000
                    with(NotificationManagerCompat.from(applicationContext)) {
                        if (it) notify(notificationId, applicationContext.notificationBuilder().build())
                        else cancel(notificationId)
                    }
                }
                .launchIn(this)
        }
    }

    private fun Context.notificationBuilder(): NotificationCompat.Builder {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent =
            PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_round)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.upload_completed))
            .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.upload_completed)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val descriptionText = getString(R.string.upload_completed)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, getString(R.string.app_name), importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT <= 28 && !Utils.allPermissionsGranted(this)) {
            Utils.requestRuntimePermissions(this)
        }
    }

    override fun onResume() {
        super.onResume()
        binding.addImagesBtn.isEnabled = true
        binding.pickPhotosView.isEnabled = true
    }
}
