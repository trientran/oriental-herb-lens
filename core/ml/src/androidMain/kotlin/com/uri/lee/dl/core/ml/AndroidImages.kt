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
import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.ml.ImageCropper
import com.uri.lee.dl.domain.ml.PhotoReader
import com.uri.lee.dl.domain.ml.ReadPhoto
import com.uri.lee.dl.domain.ml.Region
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

/** Cuts a region out of an image that still has its bitmap (camera frames and read photos do). */
internal class AndroidImageCropper : ImageCropper {
    override suspend fun crop(image: ClassifierImage, region: Region): ClassifierImage? {
        val bitmap = (image as MlKitClassifierImage).bitmap ?: return null
        val left = (region.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val top = (region.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val right = (region.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (region.bottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
        return MlKitClassifierImage(Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top))
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
