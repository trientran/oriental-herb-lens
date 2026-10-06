package com.uri.lee.dl.feature.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Image
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.khronos.webgl.Int8Array
import kotlin.math.max
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.resources.scan_allow_camera
import com.uri.lee.dl.core.designsystem.resources.scan_camera_needed
import com.uri.lee.dl.core.ml.WebClassifierImage
import com.uri.lee.dl.domain.ml.ClassifierImage
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.CanvasRenderingContext2D
import kotlin.js.Promise
import kotlin.js.json
import kotlin.math.roundToInt

/**
 * The browser's camera (the back one on phones), cropped to fill like a photo app. The `<video>`
 * stays off the page: its frames are drawn by Compose, so the buttons and results over the camera
 * show (an HTML element would sit above the app's canvas). Frames for the model are copies at
 * most 640 px on the longer side.
 */
@Composable
actual fun CameraPreview(onFrame: suspend (ClassifierImage, Float) -> Unit, modifier: Modifier) {
    val currentOnFrame by rememberUpdatedState(onFrame)
    var stream by remember { mutableStateOf<dynamic>(null) }
    var attempt by remember { mutableStateOf(0) }
    var refused by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    val video = remember {
        (document.createElement("video") as HTMLVideoElement).apply {
            autoplay = true
            muted = true
            setAttribute("playsinline", "")
        }
    }
    Box(modifier) {
        preview?.let { Image(it, contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (refused) CameraRefused(onRetry = { attempt++ }, Modifier.fillMaxSize())
    }

    LaunchedEffect(attempt) {
        refused = false
        // No camera API outside a secure context (https or localhost), or none on the device
        val media = window.navigator.asDynamic().mediaDevices
        stream = if (media == null) null else runCatching {
            media.getUserMedia(
                json("video" to json("facingMode" to json("ideal" to "environment"), "width" to json("ideal" to 1280)), "audio" to false),
            ).unsafeCast<Promise<dynamic>>().await()
        }.getOrNull()
        if (stream == null) {
            refused = true
            return@LaunchedEffect
        }
        video.asDynamic().srcObject = stream
        video.play()
        val frames = FrameCopier()
        var lastAnalysis = 0.0
        while (true) {
            val width = video.videoWidth
            val height = video.videoHeight
            if (width == 0 || height == 0 || video.readyState < 2) {
                delay(50)
                continue
            }
            val canvas = frames.draw(video, width, height)
            preview = frames.toImageBitmap(canvas)
            // The model takes ~10 ms; a few frames a second is plenty and keeps the page smooth
            val now = window.performance.now()
            if (now - lastAnalysis >= ANALYSIS_GAP_MS) {
                lastAnalysis = now
                currentOnFrame(WebClassifierImage(frames.copy(canvas)), width.toFloat() / height)
            }
            delay(PREVIEW_GAP_MS)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            stream?.getTracks()?.unsafeCast<Array<dynamic>>()?.forEach { it.stop() }
        }
    }
}

/** Refused (or no camera): the browser may ask again, unless the site was blocked in its settings. */
@Composable
private fun CameraRefused(onRetry: () -> Unit, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 360.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(Res.string.scan_camera_needed), color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onRetry) { Text(stringResource(Res.string.scan_allow_camera)) }
        }
    }
}

/** Draws video frames at most [MAX_SIDE] px wide or high, and hands them to Compose. */
private class FrameCopier {
    private val canvas = document.createElement("canvas") as HTMLCanvasElement
    private val context = canvas.getContext("2d", json("willReadFrequently" to true)) as CanvasRenderingContext2D
    // Compose may still be drawing the previous frame: free images two frames later
    private val shown = ArrayDeque<Image>()

    fun draw(video: HTMLVideoElement, width: Int, height: Int): HTMLCanvasElement {
        val scale = minOf(1.0, MAX_SIDE.toDouble() / max(width, height))
        val w = (width * scale).roundToInt()
        val h = (height * scale).roundToInt()
        if (canvas.width != w || canvas.height != h) {
            canvas.width = w
            canvas.height = h
        }
        context.drawImage(video, 0.0, 0.0, w.toDouble(), h.toDouble())
        return canvas
    }

    fun toImageBitmap(canvas: HTMLCanvasElement): ImageBitmap {
        val pixels = context.getImageData(0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble()).data
        val bytes = Int8Array(pixels.buffer, pixels.byteOffset, pixels.length).unsafeCast<ByteArray>()
        val image = Image.makeRaster(
            ImageInfo(canvas.width, canvas.height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL),
            bytes,
            canvas.width * 4,
        )
        shown.addLast(image)
        while (shown.size > 2) shown.removeFirst().close()
        return image.toComposeImageBitmap()
    }

    /** A separate copy for the model, which may still be reading it when the next frame is drawn. */
    fun copy(source: HTMLCanvasElement): HTMLCanvasElement =
        (document.createElement("canvas") as HTMLCanvasElement).apply {
            width = source.width
            height = source.height
            (getContext("2d") as CanvasRenderingContext2D).drawImage(source, 0.0, 0.0)
        }
}

private const val MAX_SIDE = 640
private const val PREVIEW_GAP_MS = 40L
private const val ANALYSIS_GAP_MS = 150.0
