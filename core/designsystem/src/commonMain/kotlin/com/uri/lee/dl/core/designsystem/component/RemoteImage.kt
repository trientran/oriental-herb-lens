package com.uri.lee.dl.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade

/** A photo from the network, on a neutral background while it loads. Coil caches it in memory and on disk. */
@Composable
fun RemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    showBackground: Boolean = true,
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalPlatformContext.current).data(url).crossfade(true).build(),
        contentDescription = contentDescription,
        modifier = if (showBackground) modifier.background(MaterialTheme.colorScheme.surfaceVariant) else modifier,
        contentScale = contentScale,
    )
}
