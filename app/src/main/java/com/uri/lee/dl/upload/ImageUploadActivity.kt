package com.uri.lee.dl.upload

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.uri.lee.dl.DeviceLocation
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

    private val startAutocomplete = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        val status = Autocomplete.getStatusFromIntent(result.data!!)
        Timber.e("SomeTagToFilterTheLogcat$status")
        if (result.resultCode == RESULT_OK) {
            val intent = result.data
            if (intent != null) {
                val place = Autocomplete.getPlaceFromIntent(intent)

                // Write a method to read the address components from the Place
                // and populate the form with the address components
                Timber.d("addressComponents: " + place.addressComponents)
                Timber.d("Place: $place")
                Timber.d("location: ${place.location}")
                Timber.d("viewport: ${place.viewport}")
                Timber.d("trien: ${place.formattedAddress}")
                viewModel.setLocation(place.toHerbLocation())
            }
        } else if (result.resultCode == RESULT_CANCELED) {
            // The user canceled the operation.
            Timber.i("User canceled autocomplete")
        }
    }

    private val locationServiceResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                lifecycleScope.launch {
                    delay(500)
                    fetchCatchingPermittedLocation(
                        onLocationFetched = { location ->
                            Timber.v("Device location: $location")
                            viewModel.setLocation(location)
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
                                viewModel.setLocation(location)
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
        binding.currentLocationBtn.setOnClickListener {
            locationPermissionResultLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
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
            it.isEnabled = false
            resultLauncher.launch(arrayOf("image/*"))
        }
        binding.searchLocationBtn.setOnClickListener {
            startAutocompleteIntent()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel
                    .state()
                    .mapNotNull { it.location }
                    .onEach { location ->
                        binding.addressLine.text = location.addressLine
                        binding.mapView.annotations.cleanup()
                        binding.mapView.mapboxMap.loadStyle(
                            style = Style.MAPBOX_STREETS,
                            onStyleLoaded = {
                                binding.mapView.addAnnotationToMap(
                                    this@ImageUploadActivity,
                                    lat = location.lat,
                                    long = location.long,
                                )
                            }
                        )
                        val cameraPosition = CameraOptions.Builder()
                            .zoom(14.0)
                            .center(Point.fromLngLat(location.long, location.lat))
                            .build()
                        // set camera position
                        binding.mapView.mapboxMap.setCamera(cameraPosition)
                    }
                    .launchIn(this)

                viewModel
                    .state()
                    .map { it.imageUris to it.location }
                    .distinctUntilChanged()
                    .onEach { (list, location) ->
                        imageUploadAdapter.submitList(list)
                        binding.uploadBtn.isVisible = list.isNotEmpty()
                        binding.locationGroup.isVisible = list.isEmpty()
                        binding.pickPhotosView.isVisible = list.isEmpty() && location != null
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

                viewModel
                    .state()
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

    private fun startAutocompleteIntent() {
        // Set the fields to specify which types of place data to
        // return after the user has made a selection.

        val fields = listOf(
            Place.Field.ADDRESS_COMPONENTS,
            Place.Field.LOCATION,
            Place.Field.VIEWPORT,
            Place.Field.FORMATTED_ADDRESS,
        )

        // Build the autocomplete intent with field, country, and type filters applied
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields)
            .setCountries(mutableListOf("AU", "VN"))
            .build(this)
        startAutocomplete.launch(intent)
    }
}

private fun MapView.addAnnotationToMap(context: Context, lat: Double, long: Double) {
// Create an instance of the Annotation API and get the PointAnnotationManager.
    bitmapFromDrawableRes(
        context,
        R.drawable.red_marker
    )?.let {
        val annotationApi = annotations
        val pointAnnotationManager = annotationApi.createPointAnnotationManager()
// Set options for the resulting symbol layer.
        val pointAnnotationOptions: PointAnnotationOptions = PointAnnotationOptions()
// Define a geographic coordinate.
            .withPoint(Point.fromLngLat(long, lat))
// Specify the bitmap you assigned to the point annotation
// The bitmap will be added to map style automatically.
            .withIconImage(it)
// Add the resulting pointAnnotation to the map.
        pointAnnotationManager.create(pointAnnotationOptions)
    }
}

private fun bitmapFromDrawableRes(context: Context, @DrawableRes resourceId: Int) =
    convertDrawableToBitmap(AppCompatResources.getDrawable(context, resourceId))

private fun convertDrawableToBitmap(sourceDrawable: Drawable?): Bitmap? {
    if (sourceDrawable == null) {
        return null
    }
    return if (sourceDrawable is BitmapDrawable) {
        sourceDrawable.bitmap
    } else {
// copying drawable object to not manipulate on the same reference
        val constantState = sourceDrawable.constantState ?: return null
        val drawable = constantState.newDrawable().mutate()
        val bitmap: Bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth, drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap
    }
}

private fun Place.toHerbLocation() = DeviceLocation(
    lat = location?.latitude ?: 0.0,
    long = location?.longitude ?: 0.0,
    addressLine = formattedAddress ?: ""
)
