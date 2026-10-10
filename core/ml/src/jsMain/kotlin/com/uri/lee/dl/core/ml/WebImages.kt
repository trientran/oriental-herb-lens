package com.uri.lee.dl.core.ml

import com.uri.lee.dl.domain.media.ImageCompressor
import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.ml.ImageCropper
import com.uri.lee.dl.domain.ml.PhotoReader
import com.uri.lee.dl.domain.ml.ReadPhoto
import com.uri.lee.dl.domain.ml.Region
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.suspendCancellableCoroutine
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import kotlin.coroutines.resume
import kotlin.js.Promise
import kotlin.js.json
import kotlin.math.max
import kotlin.math.roundToInt

/** An upright image in the browser: pixels on a canvas. */
class WebClassifierImage(val canvas: HTMLCanvasElement) : ClassifierImage

/** A photo the visitor picked, as a file; the image loader shows it through a `blob:` URL. */
class WebLocalImage(val file: Blob) : LocalImage {
    override val uri: String = URL.createObjectURL(file)
}

internal fun newCanvas(width: Int, height: Int): HTMLCanvasElement =
    (document.createElement("canvas") as HTMLCanvasElement).apply {
        this.width = width
        this.height = height
    }

internal val HTMLCanvasElement.context2d: CanvasRenderingContext2D
    get() = getContext("2d", json("willReadFrequently" to true)) as CanvasRenderingContext2D

/** Draws [source] (an image, video or canvas) onto a new canvas of the given size, smoothed as well as the browser can. */
internal fun drawn(source: dynamic, sx: Double, sy: Double, sw: Double, sh: Double, width: Int, height: Int): HTMLCanvasElement {
    val canvas = newCanvas(width, height)
    val context = canvas.context2d.asDynamic()
    context.imageSmoothingEnabled = true
    context.imageSmoothingQuality = "high"
    context.drawImage(source, sx, sy, sw, sh, 0.0, 0.0, width.toDouble(), height.toDouble())
    return canvas
}

/**
 * [source] at [size] × [size], the models' input. A large step down in one draw samples only a
 * few of the pixels it covers, and browsers do it differently (WebKit, on every iPhone browser,
 * worst), so fine detail such as leaf veins comes out noisy and results differ by browser. Halving
 * at a time averages every pixel, close to the phones' resizing.
 */
internal fun modelInput(source: HTMLCanvasElement, size: Int): HTMLCanvasElement {
    var current = source
    var width = source.width
    var height = source.height
    while (width / 2 >= size && height / 2 >= size) {
        width /= 2
        height /= 2
        current = drawn(current, 0.0, 0.0, current.width.toDouble(), current.height.toDouble(), width, height)
    }
    return drawn(current, 0.0, 0.0, current.width.toDouble(), current.height.toDouble(), size, size)
}

/**
 * Decodes [file] upright (EXIF orientation applied), at most [maxSide] pixels on its longer side.
 * Browsers differ: some reject createImageBitmap's orientation option, and some can't make a
 * bitmap from every format they can show (Safari and HEIC). So it tries, in turn, a bitmap with
 * the option, one without (most now apply the orientation anyway), then an image element, which
 * shows whatever the browser can show, upright.
 */
internal suspend fun decode(file: Blob, maxSide: Int): HTMLCanvasElement? {
    val createImageBitmap = window.asDynamic().createImageBitmap
    if (createImageBitmap != undefined) {
        for (options in listOf(json("imageOrientation" to "from-image"), null)) {
            val bitmap: dynamic = try {
                val promise = if (options != null) window.asDynamic().createImageBitmap(file, options) else window.asDynamic().createImageBitmap(file)
                promise.unsafeCast<Promise<dynamic>>().await()
            } catch (e: Throwable) {
                continue
            }
            val canvas = scaled(bitmap, bitmap.width as Int, bitmap.height as Int, maxSide)
            bitmap.close()
            return canvas
        }
    }
    return decodeWithImage(file, maxSide)
}

private suspend fun decodeWithImage(file: Blob, maxSide: Int): HTMLCanvasElement? {
    val url = URL.createObjectURL(file)
    return try {
        val image = document.createElement("img").asDynamic()
        image.src = url
        image.decode().unsafeCast<Promise<Any?>>().await()
        val width = image.naturalWidth as Int
        val height = image.naturalHeight as Int
        if (width < 1 || height < 1) null else scaled(image, width, height, maxSide)
    } catch (e: Throwable) {
        null
    } finally {
        URL.revokeObjectURL(url)
    }
}

private fun scaled(source: dynamic, width: Int, height: Int, maxSide: Int): HTMLCanvasElement {
    val scale = minOf(1.0, maxSide.toDouble() / max(width, height))
    return drawn(source, 0.0, 0.0, width.toDouble(), height.toDouble(), (width * scale).roundToInt(), (height * scale).roundToInt())
}

internal class WebPhotoReader : PhotoReader {
    override suspend fun read(photo: LocalImage): ReadPhoto? {
        // Large enough to pick plants out of, small enough to stay quick
        val canvas = decode((photo as WebLocalImage).file, maxSide = 1600) ?: return null
        return ReadPhoto(WebClassifierImage(canvas), canvas.width, canvas.height)
    }
}

internal class WebImageCropper : ImageCropper {
    override suspend fun crop(image: ClassifierImage, region: Region): ClassifierImage? {
        val source = (image as WebClassifierImage).canvas
        val left = region.left.coerceIn(0f, 1f) * source.width
        val top = region.top.coerceIn(0f, 1f) * source.height
        val width = (region.right.coerceIn(0f, 1f) * source.width - left).roundToInt()
        val height = (region.bottom.coerceIn(0f, 1f) * source.height - top).roundToInt()
        if (width < 1 || height < 1) return null
        return WebClassifierImage(drawn(source, left.toDouble(), top.toDouble(), width.toDouble(), height.toDouble(), width, height))
    }
}

/** 600 px on the longer side, 70 % JPEG: the same as the apps (about 100 KB). */
internal class WebImageCompressor : ImageCompressor {
    override suspend fun compress(image: LocalImage): ByteArray? {
        val canvas = decode((image as WebLocalImage).file, maxSide = 600) ?: return null
        val blob = suspendCancellableCoroutine<Blob?> { continuation ->
            canvas.toBlob({ continuation.resume(it) }, "image/jpeg", 0.7)
        } ?: return null
        val buffer = blob.asDynamic().arrayBuffer().unsafeCast<Promise<ArrayBuffer>>().await()
        return Int8Array(buffer).unsafeCast<ByteArray>()
    }
}
