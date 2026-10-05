package com.uri.lee.dl.core.ml

import com.uri.lee.dl.domain.media.ImageCompressor
import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.ml.ImageCropper
import com.uri.lee.dl.domain.ml.PhotoReader
import com.uri.lee.dl.domain.ml.ReadPhoto
import com.uri.lee.dl.domain.ml.Region
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGImageCreateWithImageInRect
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSURL
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy

/** An upright image with a scale of 1, so its size is in pixels. */
class IosClassifierImage(val image: UIImage) : ClassifierImage

/** A photo picked through PHPicker, copied to a temporary file. */
data class IosPickedImage(override val uri: String) : LocalImage

internal class IosPhotoReader : PhotoReader {
    override suspend fun read(photo: LocalImage): ReadPhoto? = withContext(Dispatchers.IO) {
        val image = load(photo)?.uprightScaled(maxDimension = 1024.0) ?: return@withContext null
        val (width, height) = image.pixelSize()
        ReadPhoto(IosClassifierImage(image), width, height)
    }
}

/** Cuts a region out of an upright image. */
internal class IosImageCropper : ImageCropper {
    override suspend fun crop(image: ClassifierImage, region: Region): ClassifierImage? {
        val upright = (image as IosClassifierImage).image
        val (width, height) = upright.pixelSize()
        val left = (region.left * width).toDouble().coerceIn(0.0, width - 1.0)
        val top = (region.top * height).toDouble().coerceIn(0.0, height - 1.0)
        val right = (region.right * width).toDouble().coerceIn(left + 1, width.toDouble())
        val bottom = (region.bottom * height).toDouble().coerceIn(top + 1, height.toDouble())
        return upright.cropped(left, top, right - left, bottom - top)?.let(::IosClassifierImage)
    }
}

/** 600 px on the longer side at 70 % JPEG quality, as on Android. */
internal class IosImageCompressor : ImageCompressor {
    @OptIn(ExperimentalForeignApi::class)
    override suspend fun compress(image: LocalImage): ByteArray? = withContext(Dispatchers.IO) {
        val scaled = load(image)?.uprightScaled(maxDimension = 600.0) ?: return@withContext null
        val data = UIImageJPEGRepresentation(scaled, 0.7) ?: return@withContext null
        ByteArray(data.length.toInt()).apply { usePinned { memcpy(it.addressOf(0), data.bytes, data.length) } }
    }
}

private fun load(photo: LocalImage): UIImage? {
    val path = NSURL.URLWithString(photo.uri)?.path ?: photo.uri
    return UIImage.imageWithContentsOfFile(path)
}

/**
 * Redrawn upright (camera photos are often stored rotated, with an orientation flag) at a scale
 * of 1, no larger than [maxDimension] pixels on its longer side.
 */
@OptIn(ExperimentalForeignApi::class)
fun UIImage.uprightScaled(maxDimension: Double): UIImage {
    val (width, height) = size.useContents { width to height }
    val factor = minOf(1.0, maxDimension / maxOf(width, height))
    val format = UIGraphicsImageRendererFormat.defaultFormat().apply { scale = 1.0 }
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(width * factor, height * factor), format = format)
    return renderer.imageWithActions { drawInRect(CGRectMake(0.0, 0.0, width * factor, height * factor)) }
}

@OptIn(ExperimentalForeignApi::class)
internal fun UIImage.pixelSize(): Pair<Int, Int> = size.useContents { (width * scale).toInt() to (height * scale).toInt() }

/** The part of this upright, scale-1 image inside the given pixel rectangle. */
@OptIn(ExperimentalForeignApi::class)
internal fun UIImage.cropped(left: Double, top: Double, width: Double, height: Double): UIImage? {
    val source = CGImage ?: return null
    val part = CGImageCreateWithImageInRect(source, CGRectMake(left, top, width, height)) ?: return null
    return UIImage.imageWithCGImage(part).also { CGImageRelease(part) }
}
