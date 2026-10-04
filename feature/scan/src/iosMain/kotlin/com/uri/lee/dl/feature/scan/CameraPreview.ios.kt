package com.uri.lee.dl.feature.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.resources.scan_camera_needed
import com.uri.lee.dl.core.designsystem.resources.scan_open_settings
import com.uri.lee.dl.core.ml.IosClassifierImage
import com.uri.lee.dl.core.ml.uprightScaled
import com.uri.lee.dl.domain.ml.ClassifierImage
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.stringResource
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset640x480
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectZero
import platform.CoreImage.CIContext
import platform.CoreImage.CIImage
import platform.CoreImage.createCGImage
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIImage
import platform.UIKit.UIView
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create

@Composable
actual fun CameraPreview(onFrame: suspend (ClassifierImage, Float) -> Unit, modifier: Modifier) {
    if (LocalInspectionMode.current) return Box(modifier)
    var status by remember { mutableStateOf(AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) }
    LaunchedEffect(Unit) {
        if (status == AVAuthorizationStatusNotDetermined) {
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { _ ->
                dispatch_async(dispatch_get_main_queue()) { status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) }
            }
        }
    }
    when (status) {
        AVAuthorizationStatusAuthorized -> Camera(onFrame, modifier)
        AVAuthorizationStatusNotDetermined -> Box(modifier)
        else -> Denied(modifier)
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun Camera(onFrame: suspend (ClassifierImage, Float) -> Unit, modifier: Modifier) {
    val camera = remember { CameraSession() }
    camera.onFrame = onFrame
    DisposableEffect(camera) {
        camera.start()
        onDispose { camera.stop() }
    }
    UIKitView(factory = { PreviewView(camera.session) }, modifier = modifier)
}

/** The back camera, delivering portrait frames of about 640 x 480 to [onFrame], one at a time. */
@OptIn(ExperimentalForeignApi::class)
private class CameraSession {
    val session = AVCaptureSession().apply { sessionPreset = AVCaptureSessionPreset640x480 }
    var onFrame: (suspend (ClassifierImage, Float) -> Unit)? = null
    private val frames = FrameDelegate { image, aspect -> onFrame?.let { runBlocking { it(image, aspect) } } }
    private val queue = dispatch_queue_create("herblens.camera", null)

    init {
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        val input = device?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, null) }
        if (input != null && session.canAddInput(input)) session.addInput(input)
        val output = AVCaptureVideoDataOutput().apply {
            // Frames that arrive while one is being identified are dropped, not queued
            alwaysDiscardsLateVideoFrames = true
            videoSettings = mapOf<Any?, Any?>(kCVPixelBufferPixelFormatTypeKey to kCVPixelFormatType_32BGRA)
            setSampleBufferDelegate(frames, queue)
        }
        if (session.canAddOutput(output)) session.addOutput(output)
        @Suppress("DEPRECATION") // videoRotationAngle needs iOS 17
        output.connectionWithMediaType(AVMediaTypeVideo)?.videoOrientation = AVCaptureVideoOrientationPortrait
    }

    // startRunning blocks while the camera starts, so never on the main thread
    fun start() = dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) { session.startRunning() }
    fun stop() = dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) { session.stopRunning() }
}

@OptIn(ExperimentalForeignApi::class)
private class FrameDelegate(private val onFrame: (ClassifierImage, Float) -> Unit) : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
    private val context = CIContext()

    override fun captureOutput(output: AVCaptureOutput, didOutputSampleBuffer: CMSampleBufferRef?, fromConnection: AVCaptureConnection) {
        val pixels = CMSampleBufferGetImageBuffer(didOutputSampleBuffer) ?: return
        val frame = CIImage.imageWithCVPixelBuffer(pixels)
        val cgImage = context.createCGImage(frame, fromRect = frame.extent) ?: return
        val image = UIImage.imageWithCGImage(cgImage).uprightScaled(maxDimension = 640.0)
        CGImageRelease(cgImage)
        val aspect = image.size.useContents { width / height }.toFloat()
        onFrame(IosClassifierImage(image), aspect)
    }
}

/** Shows the camera image, cropped to fill like a photo app, in step with the view's size. */
@OptIn(ExperimentalForeignApi::class)
private class PreviewView(session: AVCaptureSession) : UIView(frame = CGRectZero.readValue()) {
    private val preview = AVCaptureVideoPreviewLayer(session = session).apply { videoGravity = AVLayerVideoGravityResizeAspectFill }

    init {
        layer.addSublayer(preview)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        preview.frame = bounds
        CATransaction.commit()
    }
}

@Composable
private fun Denied(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 360.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(Res.string.scan_camera_needed), color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = {
                NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let {
                    UIApplication.sharedApplication.openURL(it, options = emptyMap<Any?, Any>(), completionHandler = null)
                }
            }) { Text(stringResource(Res.string.scan_open_settings)) }
        }
    }
}
