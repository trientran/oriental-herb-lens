package com.uri.lee.dl.feature.scan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.resources.scan_camera_unavailable
import com.uri.lee.dl.domain.ml.ClassifierImage
import org.jetbrains.compose.resources.stringResource

/** Phase 5: AVFoundation camera with ML Kit through Swift. */
@Composable
actual fun CameraPreview(onFrame: suspend (ClassifierImage, Float) -> Unit, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(stringResource(Res.string.scan_camera_unavailable), color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
    }
}
