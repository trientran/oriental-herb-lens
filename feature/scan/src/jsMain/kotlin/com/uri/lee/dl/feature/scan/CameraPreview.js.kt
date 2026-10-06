package com.uri.lee.dl.feature.scan

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.uri.lee.dl.domain.ml.ClassifierImage

@Composable
actual fun CameraPreview(onFrame: suspend (ClassifierImage, Float) -> Unit, modifier: Modifier) {
    Box(modifier)
}
