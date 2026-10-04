package com.uri.lee.dl.feature.identify

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.resources.identify_camera
import com.uri.lee.dl.core.designsystem.resources.identify_camera_body
import com.uri.lee.dl.core.designsystem.resources.identify_multiple
import com.uri.lee.dl.core.designsystem.resources.identify_multiple_body
import com.uri.lee.dl.core.designsystem.resources.identify_single
import com.uri.lee.dl.core.designsystem.resources.identify_single_body
import com.uri.lee.dl.core.designsystem.resources.identify_title
import com.uri.lee.dl.core.designsystem.theme.HerbLensTheme
import org.jetbrains.compose.resources.stringResource

enum class IdentifyMode { CAMERA, SINGLE_IMAGE, MULTIPLE_IMAGES }

/**
 * Chooses how to identify a herb. The scan screens themselves are still the Android views until
 * they move to Compose; [onStart] opens them.
 */
@Composable
fun IdentifyScreen(onStart: (IdentifyMode) -> Unit, modifier: Modifier = Modifier) {
    val spacing = HerbLensTheme.spacing
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = spacing.maxContentWidth).fillMaxWidth().verticalScroll(rememberScrollState()).padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(stringResource(Res.string.identify_title), style = MaterialTheme.typography.headlineSmall)
            ModeCard(
                Icons.Filled.CameraAlt,
                stringResource(Res.string.identify_camera),
                stringResource(Res.string.identify_camera_body),
                primary = true,
            ) { onStart(IdentifyMode.CAMERA) }
            ModeCard(Icons.Filled.Image, stringResource(Res.string.identify_single), stringResource(Res.string.identify_single_body)) {
                onStart(IdentifyMode.SINGLE_IMAGE)
            }
            ModeCard(Icons.Filled.Collections, stringResource(Res.string.identify_multiple), stringResource(Res.string.identify_multiple_body)) {
                onStart(IdentifyMode.MULTIPLE_IMAGES)
            }
        }
    }
}

@Composable
private fun ModeCard(icon: ImageVector, title: String, body: String, primary: Boolean = false, onClick: () -> Unit) {
    val spacing = HerbLensTheme.spacing
    val container = if (primary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val content = if (primary) MaterialTheme.colorScheme.onPrimaryContainer else Color.Unspecified
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container, contentColor = content)) {
        Row(Modifier.padding(spacing.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
