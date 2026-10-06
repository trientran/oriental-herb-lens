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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.HtmlElementView
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
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The browser's camera (the back one on phones) in a `<video>`, cropped to fill like a photo app.
 * Frames are copied to a canvas, at most 640 px on the longer side, for the model.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun CameraPreview(onFrame: suspend (ClassifierImage, Float) -> Unit, modifier: Modifier) {
    val currentOnFrame by rememberUpdatedState(onFrame)
    var stream by remember { mutableStateOf<dynamic>(null) }
    var attempt by remember { mutableStateOf(0) }
    var refused by remember { mutableStateOf(false) }
    val video = remember {
        (document.createElement("video") as HTMLVideoElement).apply {
            autoplay = true
            muted = true
            setAttribute("playsinline", "")
            style.width = "100%"
            style.height = "100%"
            style.setProperty("object-fit", "cover")
        }
    }
    Box(modifier) {
        HtmlElementView(factory = { video }, modifier = Modifier.fillMaxSize())
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
        while (true) {
            val width = video.videoWidth
            val height = video.videoHeight
            if (width == 0 || height == 0 || video.readyState < 2) {
                delay(100)
                continue
            }
            val scale = minOf(1.0, MAX_SIDE.toDouble() / max(width, height))
            val canvas = (document.createElement("canvas") as HTMLCanvasElement).apply {
                this.width = (width * scale).roundToInt()
                this.height = (height * scale).roundToInt()
            }
            (canvas.getContext("2d") as CanvasRenderingContext2D)
                .drawImage(video, 0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())
            currentOnFrame(WebClassifierImage(canvas), width.toFloat() / height)
            // The model takes ~10 ms; leave the page room to breathe between frames
            delay(FRAME_GAP_MS)
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

private const val MAX_SIDE = 640
private const val FRAME_GAP_MS = 150L
