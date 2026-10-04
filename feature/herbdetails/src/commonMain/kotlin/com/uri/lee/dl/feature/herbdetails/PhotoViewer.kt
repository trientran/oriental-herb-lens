package com.uri.lee.dl.feature.herbdetails

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uri.lee.dl.core.designsystem.component.RemoteImage
import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.resources.cd_close
import com.uri.lee.dl.core.designsystem.resources.cd_photo
import com.uri.lee.dl.core.designsystem.resources.photo_counter
import com.uri.lee.dl.core.designsystem.theme.HerbLensTheme
import com.uri.lee.dl.domain.model.SpeciesPhoto
import org.jetbrains.compose.resources.stringResource

/** Full-screen photos, swipeable and zoomable, each with its credit. */
@Composable
internal fun PhotoViewer(photos: List<SpeciesPhoto>, startIndex: Int, speciesName: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pager = rememberPagerState(initialPage = startIndex) { photos.size }
        val spacing = HerbLensTheme.spacing
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                ZoomablePhoto(photos[page], contentDescription = stringResource(Res.string.cd_photo, speciesName))
            }
            IconButton(onClick = onDismiss, modifier = Modifier.safeDrawingPadding().align(Alignment.TopEnd)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.cd_close), tint = Color.White)
            }
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .safeDrawingPadding()
                    .padding(spacing.lg),
            ) {
                Text(
                    stringResource(Res.string.photo_counter, pager.currentPage + 1, photos.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
                PhotoCredit(photos[pager.currentPage], MaterialTheme.typography.bodySmall, Color.White)
            }
        }
    }
}

/**
 * Pinch to zoom and pan; double-tap to reset. The smaller copy, usually cached from the photo
 * grid, shows underneath until the full-size original arrives.
 */
@Composable
private fun ZoomablePhoto(photo: SpeciesPhoto, contentDescription: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero }) }
            .transformable(transform, canPan = { scale > 1f })
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
    ) {
        if (photo.thumbnailUrl != photo.url) {
            RemoteImage(photo.thumbnailUrl, null, Modifier.fillMaxSize(), ContentScale.Fit, showBackground = false)
        }
        RemoteImage(photo.url, contentDescription, Modifier.fillMaxSize(), ContentScale.Fit, showBackground = false)
    }
}
