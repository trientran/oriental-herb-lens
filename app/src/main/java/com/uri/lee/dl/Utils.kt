/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.uri.lee.dl

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.hardware.Camera
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.exifinterface.media.ExifInterface
import com.firebase.ui.auth.AuthUI
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.google.mlkit.common.model.CustomRemoteModel
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.linkfirebase.FirebaseModelSource
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions
import com.uri.lee.dl.instantsearch.Herb
import com.uri.lee.dl.lenscamera.objectivecamera.CameraSizePair
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.time.Clock
import java.util.*
import kotlin.math.abs


/** Utility class to provide helper methods.  */
object Utils {

    /**
     * If the absolute difference between aspect ratios is less than this tolerance, they are
     * considered to be the same aspect ratio.
     */
    const val ASPECT_RATIO_TOLERANCE = 0.01f

    internal const val REQUEST_CODE_PHOTO_LIBRARY = 1
    internal const val SPEECH_REQUEST_CODE = 0

    private const val TAG = "Utils"

    internal fun requestRuntimePermissions(activity: Activity) {

        val allNeededPermissions = getRequiredPermissions(activity).filter {
            checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }

        if (allNeededPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity, allNeededPermissions.toTypedArray(), /* requestCode= */ 0
            )
        }
    }

    internal fun allPermissionsGranted(context: Context): Boolean = getRequiredPermissions(context)
        .all { checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    private fun getRequiredPermissions(context: Context): Array<String> {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            val ps = info.requestedPermissions
            if (ps != null && ps.isNotEmpty()) ps else arrayOf()
        } catch (e: Exception) {
            arrayOf()
        }
    }

    fun googleItWithDefaultBrowser(context: Context, searchedKeyWord: String) {
        val url = "https://www.google.com/search?q=$searchedKeyWord"
        val uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
    }

    fun Context.openUrlWithDefaultBrowser(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (intent.resolveActivity(packageManager) != null) startActivity(intent)
    }

    fun Context.openFacebookPage() {
        val facebookUrl = "https://www.facebook.com/100084509379824"
        try {
            val uri = Uri.parse("fb://facewebmodal/f?href=$facebookUrl")
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            openUrlWithDefaultBrowser(facebookUrl.toUri())
        }
    }

    fun isPortraitMode(context: Context): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    /**
     * Generates a list of acceptable preview sizes. Preview sizes are not acceptable if there is not
     * a corresponding picture size of the same aspect ratio. If there is a corresponding picture size
     * of the same aspect ratio, the picture size is paired up with the preview size.
     *
     *
     * This is necessary because even if we don't use still pictures, the still picture size must
     * be set to a size that is the same aspect ratio as the preview size we choose. Otherwise, the
     * preview images may be distorted on some devices.
     */
    fun generateValidPreviewSizeList(camera: Camera): List<CameraSizePair> {
        val parameters = camera.parameters
        val supportedPreviewSizes = parameters.supportedPreviewSizes
        val supportedPictureSizes = parameters.supportedPictureSizes
        val validPreviewSizes = ArrayList<CameraSizePair>()
        for (previewSize in supportedPreviewSizes) {
            val previewAspectRatio = previewSize.width.toFloat() / previewSize.height.toFloat()

            // By looping through the picture sizes in order, we favor the higher resolutions.
            // We choose the highest resolution in order to support taking the full resolution
            // picture later.
            for (pictureSize in supportedPictureSizes) {
                val pictureAspectRatio = pictureSize.width.toFloat() / pictureSize.height.toFloat()
                if (abs(previewAspectRatio - pictureAspectRatio) < ASPECT_RATIO_TOLERANCE) {
                    validPreviewSizes.add(CameraSizePair(previewSize, pictureSize))
                    break
                }
            }
        }

        // If there are no picture sizes with the same aspect ratio as any preview sizes, allow all of
        // the preview sizes and hope that the camera can handle it.  Probably unlikely, but we still
        // account for it.
        if (validPreviewSizes.isEmpty()) {
            Log.w(TAG, "No preview sizes have a corresponding same-aspect-ratio picture size.")
            for (previewSize in supportedPreviewSizes) {
                // The null picture size will let us know that we shouldn't set a picture size.
                validPreviewSizes.add(CameraSizePair(previewSize, null))
            }
        }

        return validPreviewSizes
    }

    fun getCornerRoundedBitmap(srcBitmap: Bitmap, cornerRadius: Int): Bitmap {
        val dstBitmap = Bitmap.createBitmap(srcBitmap.width, srcBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dstBitmap)
        val paint = Paint()
        paint.isAntiAlias = true
        val rectF = RectF(0f, 0f, srcBitmap.width.toFloat(), srcBitmap.height.toFloat())
        canvas.drawRoundRect(rectF, cornerRadius.toFloat(), cornerRadius.toFloat(), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(srcBitmap, 0f, 0f, paint)
        return dstBitmap
    }

    /** Convert NV21 format byte buffer to bitmap. */
    fun convertToBitmap(data: ByteBuffer, width: Int, height: Int, rotationDegrees: Int): Bitmap? {
        data.rewind()
        val imageInBuffer = ByteArray(data.limit())
        data.get(imageInBuffer, 0, imageInBuffer.size)
        try {
            val image = YuvImage(
                imageInBuffer, InputImage.IMAGE_FORMAT_NV21, width, height, null
            )
            val stream = ByteArrayOutputStream()
            image.compressToJpeg(Rect(0, 0, width, height), 80, stream)
            val bmp = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size())
            stream.close()

            // Rotate the image back to straight.
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Error: " + e.message)
        }
        return null
    }

    internal suspend fun Context.loadBitmapFromUri(imageUri: Uri, maxImageDimension: Int): Bitmap? = runInterruptible(
        ioDispatcher
    ) {
        var inputStreamForSize: InputStream? = null
        var inputStreamForImage: InputStream? = null
        try {
            inputStreamForSize = contentResolver.openInputStream(imageUri)
            var opts = BitmapFactory.Options()
            opts.inJustDecodeBounds = true
            BitmapFactory.decodeStream(inputStreamForSize, null, opts)/* outPadding= */
            val inSampleSize = (opts.outWidth / maxImageDimension).coerceAtMost(opts.outHeight / maxImageDimension)
            opts = BitmapFactory.Options()
            opts.inSampleSize = inSampleSize
            inputStreamForImage = contentResolver.openInputStream(imageUri)
            val decodedBitmap = BitmapFactory.decodeStream(inputStreamForImage, null, opts)/* outPadding= */
            maybeTransformBitmap(contentResolver, imageUri, decodedBitmap)
        } finally {
            inputStreamForSize?.close()
            inputStreamForImage?.close()
        }
    }

    internal suspend fun Context.compressToJpgByteArray(
        uri: Uri,
        maxImageDimension: Int = 600,
        compressingPercentage: Int = 70,
    ): ByteArray? = withContext(ioDispatcher) {
        var bitmap: Bitmap? = null
        try {
            bitmap = loadBitmapFromUri(imageUri = uri, maxImageDimension = maxImageDimension)
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap?.compress(Bitmap.CompressFormat.JPEG, compressingPercentage, byteArrayOutputStream)
            val byteArray: ByteArray = byteArrayOutputStream.toByteArray()
            bitmap?.recycle()
            return@withContext byteArray
        } catch (cancellationException: CancellationException) {
            return@withContext null
        } catch (e: Exception) {
            Timber.e(e)
            return@withContext null
        } finally {
            bitmap?.recycle()
        }
    }

    // desiredPrefix should be uid
    internal suspend fun Context.compressToJpgFile(
        uri: Uri,
        desiredPrefix: String,
        maxImageDimension: Int = 600,
        compressingPercentage: Int = 70,
    ): Uri? = withContext(ioDispatcher) {
        var compressedFile: File? = null
        var bitmap: Bitmap? = null
        try {
            compressedFile = this@compressToJpgFile.generateTempCameraFile(desiredPrefix)
            bitmap = loadBitmapFromUri(imageUri = uri, maxImageDimension = maxImageDimension)
            bitmap?.compress(Bitmap.CompressFormat.JPEG, compressingPercentage, compressedFile.outputStream())
            bitmap?.recycle()
            return@withContext compressedFile.toUri()
        } catch (cancellationException: CancellationException) {
            compressedFile?.delete()
            return@withContext null
        } catch (e: Exception) {
            Timber.e(e)
            compressedFile?.delete()
            return@withContext null
        } finally {
            bitmap?.recycle()
        }
    }

    private fun Context.generateTempCameraFile(prefix: String, suffix: String = ".jpg"): File {
        var dir = cacheDir
        dir = File(dir, "camera_images")
        dir.mkdirs()
        return File.createTempFile(prefix, suffix, dir)
    }

    internal fun Context.getImageDimension(imageUri: Uri): Pair<Int, Int>? {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeStream(contentResolver.openInputStream(imageUri), null, options)/* outPadding= */
        return options.outHeight to options.outWidth
    }

    internal fun displaySpeechRecognizer(activity: Activity) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        // This starts the activity and populates the intent with the speech text.
        startActivityForResult(activity, intent, SPEECH_REQUEST_CODE, null)
    }

    private fun maybeTransformBitmap(resolver: ContentResolver, uri: Uri, bitmap: Bitmap?): Bitmap? {
        val matrix: Matrix? = when (getExifOrientationTag(resolver, uri)) {
            ExifInterface.ORIENTATION_UNDEFINED, ExifInterface.ORIENTATION_NORMAL ->
                // Set the matrix to be null to skip the image transform.
                null
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { postScale(-1.0f, 1.0f) }

            ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { postRotate(90f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> Matrix().apply { postScale(-1.0f, 1.0f) }
            ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { postRotate(180.0f) }
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply { postScale(1.0f, -1.0f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { postRotate(-90.0f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> Matrix().apply {
                postRotate(-90.0f)
                postScale(-1.0f, 1.0f)
            }
            else ->
                // Set the matrix to be null to skip the image transform.
                null
        }

        return if (matrix != null) {
            Bitmap.createBitmap(bitmap!!, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    private fun getExifOrientationTag(resolver: ContentResolver, imageUri: Uri): Int {
        if (ContentResolver.SCHEME_CONTENT != imageUri.scheme && ContentResolver.SCHEME_FILE != imageUri.scheme) {
            return 0
        }

        var exif: ExifInterface? = null
        try {
            resolver.openInputStream(imageUri)?.use { inputStream -> exif = ExifInterface(inputStream) }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to open file to read rotation meta data: $imageUri", e)
        }

        return if (exif != null) {
            exif!!.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } else {
            ExifInterface.ORIENTATION_UNDEFINED
        }
    }
}

fun Context.hideSoftKeyboard(view: View) {
    val imm = getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(view.windowToken, 0)
}

fun Uri.toScaledBitmap(context: Context, width: Int = 224, height: Int = 224): Bitmap? {
    val bitmapFromUri: Bitmap? =
        // check version of Android on device
        try {
            if (Build.VERSION.SDK_INT > 27) {
                // on newer versions of Android, use the new decodeBitmap method
                val source: ImageDecoder.Source =
                    ImageDecoder.createSource(context.contentResolver, this)
                ImageDecoder.decodeBitmap(source)
            } else {
                // support older versions of Android by using getBitmap
                MediaStore.Images.Media.getBitmap(context.contentResolver, this)
            }
        } catch (e: Exception) {
            Timber.e(e.message ?: "Some error")
            null
        }

    val resizedBitmap = bitmapFromUri?.let { Bitmap.createScaledBitmap(it, width, height, false) }

    return resizedBitmap?.copy(Bitmap.Config.ARGB_8888, true)
}

fun getHerbModel(optionsBuilderCallBack: (CustomImageLabelerOptions.Builder) -> Unit) {
    val localModel = LocalModel.Builder().setAssetFilePath(LOCAL_TFLITE_MODEL_NAME).build()
    // Specify the name you assigned in the Firebase console.
    val remoteModel = CustomRemoteModel
        .Builder(FirebaseModelSource.Builder(REMOTE_TFLITE_MODEL_NAME).build())
        .build()
    RemoteModelManager.getInstance().isModelDownloaded(remoteModel)
        .addOnSuccessListener { isDownloaded ->
            val optionsBuilder =
                if (isDownloaded) {
                    Timber.d("Remote model being used")
                    CustomImageLabelerOptions.Builder(remoteModel)
                } else {
                    Timber.d("Local model being used")
                    CustomImageLabelerOptions.Builder(localModel)
                }
            optionsBuilderCallBack.invoke(optionsBuilder)
        }
}

class AuthStateListener(val context: Context) : FirebaseAuth.AuthStateListener {
    override fun onAuthStateChanged(auth: FirebaseAuth) {
        Timber.d(auth.currentUser.toString())
        auth.currentUser ?: ContextCompat.startActivity(context, Intent(context, LoginActivity::class.java), null)
    }
}

const val INSTANT_HERB = "INSTANT_HERB"
const val CAMERA_PERMISSION = Manifest.permission.CAMERA
const val READ_EXTERNAL_STORAGE_PERMISSION = Manifest.permission.READ_EXTERNAL_STORAGE
const val LOCAL_TFLITE_MODEL_NAME = "model_70.tflite"
const val REMOTE_TFLITE_MODEL_NAME = "herb_model"
val defaultDispatcher = Dispatchers.Default
val mainDispatcher = Dispatchers.Main
val ioDispatcher = Dispatchers.IO
const val MAX_IMAGE_DIMENSION_FOR_OBJECT_DETECTION = 1024
const val MAX_IMAGE_DIMENSION_FOR_LABELING = 600

val globalScope = CoroutineScope(SupervisorJob() + defaultDispatcher)

// data store stuff
const val SETTINGS = "SETTINGS"
val IS_OBJECTS_MODE_SINGLE_IMAGE = booleanPreferencesKey("IS_OBJECTS_MODE")
val CONFIDENCE_LEVEL = floatPreferencesKey("CONFIDENCE_LEVEL")
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = SETTINGS)

val authUI = AuthUI.getInstance()

val clock: Clock = Clock.systemDefaultZone()

val db = Firebase.firestore
val herbCollection = db.collection("herbs") // dont change this value
val userCollection = db.collection("users") // dont change this value
val uploadCollection = db.collection("uploads") // dont change this value
const val USER_FAVORITE_FIELD_NAME = "favorite" // dont change this value
const val USER_HISTORY_FIELD_NAME = "history" // dont change this value
val storage = Firebase.storage
var herbStorage = storage.reference.child("herbs")
const val HERB_ID = "HERB_ID"

fun Context.isNetworkAvailable(): Boolean {
    val connectivityManager =
        getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
    val networkCapabilities = connectivityManager.activeNetwork ?: return false
    val actNw = connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
    return when {
        actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
        actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
        actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
        else -> false
    }
}

val systemLanguageLowercase = Locale.getDefault().displayLanguage.lowercase()
val isSystemLanguageVietnamese =
    systemLanguageLowercase == "vietnamese" || systemLanguageLowercase == "vi" || systemLanguageLowercase == "tiếng việt"

fun DocumentSnapshot.toHerb() = Herb(
    objectID = id,
    id = id,
    enDosing = getString("enDosing"),
    enInteractions = getString("enInteractions"),
    enName = getString("enName"),
    enOverview = getString("enOverview"),
    enSideEffects = getString("enSideEffects"),
    latinName = getString("latinName"),
    viDosing = getString("viDosing"),
    viInteractions = getString("viInteractions"),
    viName = getString("viName"),
    viOverview = getString("viOverview"),
    viSideEffects = getString("viSideEffects"),
)

fun DocumentSnapshot.toLikes() = get(USER_FAVORITE_FIELD_NAME) as? List<*>
fun DocumentSnapshot.toHistory() = (get(USER_HISTORY_FIELD_NAME) as? List<*>) ?: emptyList<Any>()

// Storj
var satelliteAddress: String? = "us-central-1.tardigrade.io:7777"
var serializedApiKey = "13Yqft7v..."
var passphrase = "super secret passphrase"


class BaseApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}
