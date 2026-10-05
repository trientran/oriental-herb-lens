package com.uri.lee.dl.feature.scan

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.uri.lee.dl.domain.ml.ClassifierImage

/**
 * The live camera, filling [modifier] (cropped to fill, like a photo app). Asks for the camera
 * permission itself. Each analysed frame, upright, goes to [onFrame] with its aspect ratio
 * (width / height); the next frame waits until [onFrame] returns.
 */
@Composable
expect fun CameraPreview(onFrame: suspend (ClassifierImage, Float) -> Unit, modifier: Modifier = Modifier)
