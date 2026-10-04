package com.uri.lee.dl.core.ml

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.uri.lee.dl.domain.media.ImageCompressor
import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.domain.ml.PhotoReader
import com.uri.lee.dl.domain.ml.ReadPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.ByteArrayOutputStream
import java.io.IOException

/** A photo picked through the system picker. */
data class UriImage(val contentUri: Uri) : LocalImage {
    override val uri: String get() = contentUri.toString()
}

/** 600 px on the longer side at 70 % JPEG quality, as uploads have always been. */
internal class AndroidImageCompressor(private val context: Context) : ImageCompressor {
    override suspend fun compress(image: LocalImage): ByteArray? {
        val bitmap = context.loadUprightBitmap((image as UriImage).contentUri, maxDimension = 600) ?: return null
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }
}

/** Large enough for object detection to find small plants, small enough to stay quick. */
internal class AndroidPhotoReader(private val context: Context) : PhotoReader {
    override suspend fun read(photo: LocalImage): ReadPhoto? {
        val bitmap = context.loadUprightBitmap((photo as UriImage).contentUri, maxDimension = 1024) ?: return null
        return ReadPhoto(MlKitClassifierImage(bitmap), bitmap.width, bitmap.height)
    }
}

/**
 * Decodes [uri] downsampled to about [maxDimension] on its longer side and turned upright by its
 * EXIF orientation. Null if it can't be read.
 */
internal suspend fun Context.loadUprightBitmap(uri: Uri, maxDimension: Int): Bitmap? = runInterruptible(Dispatchers.IO) {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runInterruptible null
        val options = BitmapFactory.Options().apply {
            inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / maxDimension).coerceAtLeast(1)
        }
        val decoded = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return@runInterruptible null
        val matrix = orientationMatrix(exifOrientation(contentResolver, uri)) ?: return@runInterruptible decoded
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null // the picker's read grant has expired
    }
}

private fun exifOrientation(resolver: ContentResolver, uri: Uri): Int = try {
    resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        ?: ExifInterface.ORIENTATION_NORMAL
} catch (e: IOException) {
    ExifInterface.ORIENTATION_NORMAL
}

private fun orientationMatrix(orientation: Int): Matrix? = when (orientation) {
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { postScale(-1f, 1f) }
    ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { postRotate(90f) }
    ExifInterface.ORIENTATION_TRANSPOSE -> Matrix().apply { postRotate(90f); postScale(-1f, 1f) }
    ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { postRotate(180f) }
    ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply { postScale(1f, -1f) }
    ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { postRotate(-90f) }
    ExifInterface.ORIENTATION_TRANSVERSE -> Matrix().apply { postRotate(-90f); postScale(-1f, 1f) }
    else -> null
}
