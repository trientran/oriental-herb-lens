package com.uri.lee.dl.upload

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.uri.lee.dl.HERB_ID
import com.uri.lee.dl.MainActivity
import com.uri.lee.dl.R
import com.uri.lee.dl.Utils
import com.uri.lee.dl.databinding.ActivityImageUploadBinding
import com.uri.lee.dl.fetchCatchingPermittedLocation
import com.uri.lee.dl.foreground
import com.uri.lee.dl.isLocationServiceEnabled
import com.uri.lee.dl.requestLocationEnabled
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import timber.log.Timber

class ImageUploadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageUploadBinding

    private lateinit var imageUploadAdapter: ImageUploadAdapter

    private val viewModel: ImageUploadViewModel by viewModels()

    private var resultLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { list ->
        viewModel.addImageUris(list)
    }

    private val locationServiceResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                lifecycleScope.launch {
                    delay(500)
                    fetchCatchingPermittedLocation(
                        onLocationFetched = { location ->
                            Timber.v("Device location: $location")
                            viewModel.setDeviceLocation(location)
                        },
                        onFailure = { it.printStackTrace() }
                    )
                }
            } else {
                Toast.makeText(this, "Location service is not available", Toast.LENGTH_LONG).show()
            }
        }

    private var locationPermissionResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            Timber.v("Location permission result: $result")
            val isGranted = result.any { it.value }
            lifecycleScope.launch {
                if (isGranted) {
                    if (isLocationServiceEnabled()) {
                        fetchCatchingPermittedLocation(
                            onLocationFetched = { location ->
                                Timber.v("Device location: $location")
                                viewModel.setDeviceLocation(location)
                            },
                            onFailure = { it.printStackTrace() }
                        )
                    } else {
                        this@ImageUploadActivity.requestLocationEnabled(
                            locationPermissionResultLauncher = locationServiceResultLauncher,
                            coroutineScope = lifecycleScope,
                        )
                    }
                } else {
                    AlertDialog.Builder(this@ImageUploadActivity)
                        .setMessage(getString(R.string.location_permission_is_required))
                        .setCancelable(true)
                        .setNeutralButton("OK") { dialog, _ -> dialog.dismiss() }
                        .create().show()
                }
            }
        }

    private val mainScope = MainScope()
    private val channelId = "image_upload"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageUploadBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        intent.getLongExtra(HERB_ID, 1001).let { viewModel.setHerbId(it) }
        imageUploadAdapter = ImageUploadAdapter {
            val bottomSheet = FullSizeImageViewerDialog(it)
            bottomSheet.show(supportFragmentManager, "ModalBottomSheet")
        }

        val gridLayoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
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
            resultLauncher.launch(arrayOf("image/*"))
        }
        binding.pickPhotosView.setOnClickListener {
//            it.isEnabled = false
//            resultLauncher.launch(arrayOf("image/*"))

            locationPermissionResultLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }


//        if (isLocationPermissionGranted()) {
//            ActivityCompat.requestPermissions(
//                this,
//                arrayOf(
//                    Manifest.permission.ACCESS_FINE_LOCATION,
//                    Manifest.permission.ACCESS_COARSE_LOCATION
//                ),
//                1001
//            )
//        } else if (!isLocationServiceEnabled()) {
//            openLocationSettings()
//        } else {
//            // fetch location
//            mainScope.launch {
//                lastLocation().let {
//                    Timber.v("Device location: $it")
//                }
//            }
//        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state()
                    .map { it.imageUris }
                    .distinctUntilChanged()
                    .onEach { list ->
                        imageUploadAdapter.submitList(list)
                        binding.uploadBtn.isVisible = list.isNotEmpty()
                        binding.pickPhotosView.isVisible = list.isEmpty()
                        binding.clearBtn.isVisible = list.isNotEmpty()
                        binding.uploadBtn.setOnClickListener {
                            it.isEnabled = false
                            viewModel.uploadSequentially()
                            Toast.makeText(
                                this@ImageUploadActivity,
                                getString(R.string.upload_in_progress_please),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    .launchIn(this)

                viewModel.state()
                    .mapNotNull { it.uploadedImagesCount }
                    .distinctUntilChanged()
                    .onEach { count ->
                        setOf(
                            binding.addImagesBtn,
                            binding.clearBtn,
                            binding.uploadBtn,
                            binding.pickPhotosView,
                            binding.instructionView,
                            binding.recyclerView
                        ).onEach { it.visibility = View.GONE }
                        binding.progressTextView.isVisible = true
                        binding.progressTextView.text = getString(
                            R.string.n_images_have_been_uploaded_etc,
                            "$count/${imageUploadAdapter.itemCount}"
                        )
                    }
                    .launchIn(this)
            }
        }

        createNotificationChannel()
        mainScope.launch {
            combine(viewModel.state().map { it.isUploadComplete }.filter { it },
                foreground()
            ) { isUploadComplete, isForeground ->
                isUploadComplete to isForeground
            }
                .distinctUntilChanged()
                .take(1)
                .onEach { (isUploadComplete, isForeground) ->
                    val notificationId = 1000
                    with(NotificationManagerCompat.from(applicationContext)) {
                        if (isForeground && isUploadComplete) {
                            Toast.makeText(
                                this@ImageUploadActivity,
                                getString(R.string.upload_completed),
                                Toast.LENGTH_LONG
                            ).show()
                            return@onEach
                        }
                        if (ActivityCompat.checkSelfPermission(
                                this@ImageUploadActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            // TODO: Consider calling
                            //    ActivityCompat#requestPermissions
                            // here to request the missing permissions, and then overriding
                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                            //                                          int[] grantResults)
                            // to handle the case where the user grants the permission. See the documentation
                            // for ActivityCompat#requestPermissions for more details.
                            return@with
                        }
                        if (isUploadComplete) notify(
                            notificationId,
                            applicationContext.notificationBuilder().build()
                        ) else cancel(notificationId)
                    }
                }
                .launchIn(this)
            viewModel.state()
                .map { it.isUploadComplete }
                .distinctUntilChanged()
                .onEach {

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
        val descriptionText = getString(R.string.upload_completed)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, getString(R.string.app_name), importance).apply {
            description = descriptionText
        }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    override fun onStart() {
        super.onStart()
        Utils.requestReadExternalStoragePermission(this)
        Utils.requestNotificationPermission(this)
    }

    override fun onResume() {
        super.onResume()
        binding.addImagesBtn.isEnabled = true
        binding.pickPhotosView.isEnabled = true
    }
}
