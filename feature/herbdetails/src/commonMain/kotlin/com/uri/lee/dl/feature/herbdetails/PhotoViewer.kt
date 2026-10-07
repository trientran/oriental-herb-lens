package com.uri.lee.dl.feature.herbdetails

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uri.lee.dl.core.designsystem.component.DialogLayer
import com.uri.lee.dl.core.designsystem.component.RemoteImage
import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.resources.cancel
import com.uri.lee.dl.core.designsystem.resources.cd_close
import com.uri.lee.dl.core.designsystem.resources.cd_more
import com.uri.lee.dl.core.designsystem.resources.cd_photo
import com.uri.lee.dl.core.designsystem.resources.hide
import com.uri.lee.dl.core.designsystem.resources.hide_contributor
import com.uri.lee.dl.core.designsystem.resources.hide_contributor_body
import com.uri.lee.dl.core.designsystem.resources.hide_contributor_title
import com.uri.lee.dl.core.designsystem.resources.photo_counter
import com.uri.lee.dl.core.designsystem.resources.report_body
import com.uri.lee.dl.core.designsystem.resources.report_photo
import com.uri.lee.dl.core.designsystem.resources.report_reason_not_plant
import com.uri.lee.dl.core.designsystem.resources.report_reason_other
import com.uri.lee.dl.core.designsystem.resources.report_reason_person
import com.uri.lee.dl.core.designsystem.resources.report_reason_sexual_violent
import com.uri.lee.dl.core.designsystem.resources.report_reason_wrong_species
import com.uri.lee.dl.core.designsystem.resources.report_send
import com.uri.lee.dl.core.designsystem.resources.report_sign_in
import com.uri.lee.dl.core.designsystem.resources.profile_sign_in
import com.uri.lee.dl.core.designsystem.resources.report_title
import com.uri.lee.dl.core.designsystem.theme.HerbLensTheme
import com.uri.lee.dl.domain.model.PhotoSource
import com.uri.lee.dl.domain.model.SpeciesPhoto
import com.uri.lee.dl.domain.moderation.ReportReason
import org.jetbrains.compose.resources.stringResource

/**
 * Full-screen photos, swipeable and zoomable, each with its credit. Photos users shared can be
 * reported, and their contributor hidden.
 */
@Composable
internal fun PhotoViewer(
    photos: List<SpeciesPhoto>,
    startIndex: Int,
    speciesName: String,
    onDismiss: () -> Unit,
    onReport: (SpeciesPhoto, ReportReason) -> Unit = { _, _ -> },
    onHideContributor: (String) -> Unit = {},
    /** Set while signed out: Report asks the user to sign in first. */
    onSignInToReport: (() -> Unit)? = null,
) {
    DialogLayer {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val pager = rememberPagerState(initialPage = startIndex) { photos.size }
            val spacing = HerbLensTheme.spacing
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                    ZoomablePhoto(photos[page], contentDescription = stringResource(Res.string.cd_photo, speciesName))
                }
                PagerArrows(pager)
                Row(Modifier.safeDrawingPadding().align(Alignment.TopEnd)) {
                    val current = photos.getOrNull(pager.currentPage)
                    if (current?.source == PhotoSource.USER) ModerationMenu(current, onReport, onHideContributor, onSignInToReport)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.cd_close), tint = Color.White)
                    }
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

/** Report the photo, or hide everything its contributor shared (App Store guideline 1.2). */
@Composable
private fun ModerationMenu(
    photo: SpeciesPhoto,
    onReport: (SpeciesPhoto, ReportReason) -> Unit,
    onHideContributor: (String) -> Unit,
    onSignInToReport: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var hiding by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.cd_more), tint = Color.White)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.report_photo)) },
                leadingIcon = { Icon(Icons.Filled.Flag, null) },
                onClick = { menu = false; reporting = true },
            )
            photo.uploaderId?.let {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.hide_contributor)) },
                    leadingIcon = { Icon(Icons.Filled.Block, null) },
                    onClick = { menu = false; hiding = true },
                )
            }
        }
    }
    if (reporting && onSignInToReport != null) {
        // Hiding the contributor needs no account; sending a report does
        AlertDialog(
            onDismissRequest = { reporting = false },
            title = { Text(stringResource(Res.string.report_title)) },
            text = { Text(stringResource(Res.string.report_sign_in)) },
            confirmButton = { TextButton(onClick = { reporting = false; onSignInToReport() }) { Text(stringResource(Res.string.profile_sign_in)) } },
            dismissButton = { TextButton(onClick = { reporting = false }) { Text(stringResource(Res.string.cancel)) } },
        )
    } else if (reporting) {
        ReportDialog(onDismiss = { reporting = false }) { reason -> reporting = false; onReport(photo, reason) }
    }
    val uploader = photo.uploaderId
    if (hiding && uploader != null) {
        AlertDialog(
            onDismissRequest = { hiding = false },
            title = { Text(stringResource(Res.string.hide_contributor_title)) },
            text = { Text(stringResource(Res.string.hide_contributor_body)) },
            confirmButton = { TextButton(onClick = { hiding = false; onHideContributor(uploader) }) { Text(stringResource(Res.string.hide)) } },
            dismissButton = { TextButton(onClick = { hiding = false }) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

@Composable
private fun ReportDialog(onDismiss: () -> Unit, onReport: (ReportReason) -> Unit) {
    var reason by remember { mutableStateOf<ReportReason?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.report_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                Text(stringResource(Res.string.report_body), style = MaterialTheme.typography.bodyMedium)
                ReportReason.entries.forEach { entry ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = reason == entry, onClick = { reason = entry }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == entry, onClick = null)
                        Text(stringResource(entry.label()), Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { reason?.let(onReport) }, enabled = reason != null) { Text(stringResource(Res.string.report_send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

private fun ReportReason.label() = when (this) {
    ReportReason.NOT_A_PLANT -> Res.string.report_reason_not_plant
    ReportReason.SEXUAL_OR_VIOLENT -> Res.string.report_reason_sexual_violent
    ReportReason.SHOWS_A_PERSON -> Res.string.report_reason_person
    ReportReason.WRONG_SPECIES -> Res.string.report_reason_wrong_species
    ReportReason.OTHER -> Res.string.report_reason_other
}
