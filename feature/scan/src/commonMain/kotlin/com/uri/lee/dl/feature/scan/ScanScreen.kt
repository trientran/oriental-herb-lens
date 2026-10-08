package com.uri.lee.dl.feature.scan

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.uri.lee.dl.core.designsystem.LocalVietnameseFirst
import com.uri.lee.dl.core.designsystem.component.ConfidenceChip
import com.uri.lee.dl.core.designsystem.component.DialogLayer
import com.uri.lee.dl.core.designsystem.component.HerbCard
import com.uri.lee.dl.core.designsystem.component.RemoteImage
import com.uri.lee.dl.core.designsystem.component.ScientificName
import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.resources.cd_close
import com.uri.lee.dl.core.designsystem.resources.cd_picked_plant
import com.uri.lee.dl.core.designsystem.resources.cd_plant
import com.uri.lee.dl.core.designsystem.resources.scan_again
import com.uri.lee.dl.core.designsystem.resources.scan_back_to_camera
import com.uri.lee.dl.core.designsystem.resources.scan_hold_steady
import com.uri.lee.dl.core.designsystem.resources.scan_looking
import com.uri.lee.dl.core.designsystem.resources.scan_notice_body
import com.uri.lee.dl.core.designsystem.resources.scan_notice_help
import com.uri.lee.dl.core.designsystem.resources.scan_notice_help_in_apps
import com.uri.lee.dl.core.designsystem.resources.scan_notice_line
import com.uri.lee.dl.core.designsystem.resources.scan_notice_more
import com.uri.lee.dl.core.designsystem.resources.scan_notice_ok
import com.uri.lee.dl.core.designsystem.resources.scan_notice_title
import com.uri.lee.dl.core.designsystem.resources.scan_identify_failed
import com.uri.lee.dl.core.designsystem.resources.scan_photo_failed
import com.uri.lee.dl.core.designsystem.resources.scan_photo_none
import com.uri.lee.dl.core.designsystem.resources.scan_photo_none_pick
import com.uri.lee.dl.core.designsystem.resources.scan_photos
import com.uri.lee.dl.core.designsystem.resources.scan_pick_plant
import com.uri.lee.dl.core.designsystem.resources.scan_plant_unknown
import com.uri.lee.dl.core.designsystem.resources.scan_point
import com.uri.lee.dl.core.designsystem.resources.scan_preparing
import com.uri.lee.dl.core.designsystem.resources.scan_tap_plant
import com.uri.lee.dl.core.designsystem.resources.scan_unknown_species
import com.uri.lee.dl.core.designsystem.resources.scan_whole_view
import com.uri.lee.dl.core.designsystem.resources.scan_working
import com.uri.lee.dl.core.designsystem.theme.HerbLensTheme
import com.uri.lee.dl.domain.media.PhotoPick
import com.uri.lee.dl.domain.media.PickPhotos
import com.uri.lee.dl.domain.model.RecognizedHerb
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Identify a herb: the camera fills the screen, or a picked photo. Results show in a panel at the
 * bottom (beside the image on wide windows); tapping one opens the species.
 */
@Composable
fun ScanRoute(
    pickPhotos: PickPhotos,
    onOpenSpecies: (Long) -> Unit,
    modifier: Modifier = Modifier,
    canSharePhotos: Boolean = true,
) {
    val viewModel = koinViewModel<ScanViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScanScreen(
        state = state,
        onAction = viewModel::onAction,
        onPickPhotos = {
            pickPhotos(
                PhotoPick(onPreparing = { viewModel.onAction(ScanAction.PhotosPreparing(it)) }) {
                    viewModel.onAction(ScanAction.PhotosPicked(it))
                },
            )
        },
        onOpenSpecies = { id ->
            viewModel.onAction(ScanAction.ResultOpened(id))
            onOpenSpecies(id)
        },
        modifier = modifier,
        canSharePhotos = canSharePhotos,
        camera = { CameraPreview(onFrame = viewModel::analyzeFrame, modifier = Modifier.fillMaxSize()) },
    )
}

@Composable
fun ScanScreen(
    state: ScanState,
    onAction: (ScanAction) -> Unit,
    onPickPhotos: () -> Unit,
    onOpenSpecies: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** Whether this platform shares photos; where it doesn't (the web), the notice points to the apps. */
    canSharePhotos: Boolean = true,
    camera: @Composable () -> Unit = {},
) {
    Box(modifier.fillMaxSize()) {
        val source = state.source
        if (source is ScanSource.Photos) {
            Batch(source, onPickPhotos, { onAction(ScanAction.BackToCamera) }, { onAction(ScanAction.ShowNotice) }, onOpenSpecies)
        } else {
            Single(state, source, onAction, onPickPhotos, onOpenSpecies, camera)
        }
        if (state.preparingPhotos > 0) Preparing(state.preparingPhotos)
        if (state.showNotice) NoticeDialog(canSharePhotos) { onAction(ScanAction.DismissNotice) }
    }
}

/** Why results are often wrong: the herb model is a research preview, with too few photos yet. */
@Composable
private fun NoticeDialog(canSharePhotos: Boolean, onDismiss: () -> Unit) {
    DialogLayer {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Outlined.Science, contentDescription = null) },
            title = { Text(stringResource(Res.string.scan_notice_title)) },
            text = {
                val help = stringResource(if (canSharePhotos) Res.string.scan_notice_help else Res.string.scan_notice_help_in_apps)
                Text(
                    stringResource(Res.string.scan_notice_body) + "\n\n" + help,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.scan_notice_ok)) } },
        )
    }
}

/** A standing reminder under the results, opening [NoticeDialog]. */
@Composable
private fun NoticeLine(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(Res.string.scan_notice_line),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(stringResource(Res.string.scan_notice_more), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** The camera or one photo, with the plants found on it and what they may be. */
@Composable
private fun Single(
    state: ScanState,
    source: ScanSource,
    onAction: (ScanAction) -> Unit,
    onPickPhotos: () -> Unit,
    onOpenSpecies: (Long) -> Unit,
    camera: @Composable () -> Unit,
) {
    val wide = currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
    Row(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color.Black)
                .onSizeChanged { if (it.height > 0) onAction(ScanAction.ViewAspect(it.width.toFloat() / it.height)) },
        ) {
            when (source) {
                ScanSource.Camera -> camera()
                is ScanSource.Photo -> RemoteImage(source.uri, null, Modifier.fillMaxSize(), ContentScale.Fit, showBackground = false)
                is ScanSource.Photos -> Unit
            }
            Objects(state, onAction)
            if (state.canPickPlants) {
                ModeSwitch(state.mode, { onAction(ScanAction.SetMode(it)) }, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp))
            }
            Actions(source, onPickPhotos, { onAction(ScanAction.BackToCamera) }, Modifier.align(if (wide) Alignment.BottomCenter else Alignment.TopEnd))
            if (!wide) {
                ResultsPanel(state, onAction, onOpenSpecies, Modifier.align(Alignment.BottomCenter).padding(12.dp).widthIn(max = 560.dp).fillMaxWidth())
            }
        }
        if (wide) {
            Surface(Modifier.width(380.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
                // A Surface stretches its child to its own size; the column lets the panel fit its content
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ResultsPanel(state, onAction, onOpenSpecies, Modifier.statusBarsPadding().padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ModeSwitch(mode: ScanMode, onChange: (ScanMode) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
        // Fixed width: the labels need it, and it fits a 360dp phone
        SingleChoiceSegmentedButtonRow(Modifier.padding(4.dp).width(300.dp)) {
            ScanMode.entries.forEachIndexed { i, entry ->
                SegmentedButton(
                    selected = mode == entry,
                    onClick = { onChange(entry) },
                    shape = SegmentedButtonDefaults.itemShape(i, ScanMode.entries.size),
                    // The filled segment shows the choice; a tick would squeeze the labels
                    icon = {},
                ) {
                    Text(stringResource(if (entry == ScanMode.WHOLE_VIEW) Res.string.scan_whole_view else Res.string.scan_pick_plant), maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun Actions(source: ScanSource, onPickPhotos: () -> Unit, onBackToCamera: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.statusBarsPadding().padding(top = 72.dp, end = 12.dp, start = 12.dp, bottom = 12.dp), horizontalAlignment = Alignment.End) {
        if (source is ScanSource.Photo) {
            ExtendedFloatingActionButton(
                onClick = onBackToCamera,
                icon = { Icon(Icons.Filled.CameraAlt, null) },
                text = { Text(stringResource(Res.string.scan_back_to_camera)) },
            )
        } else {
            ExtendedFloatingActionButton(
                onClick = onPickPhotos,
                icon = { Icon(Icons.Filled.PhotoLibrary, null) },
                text = { Text(stringResource(Res.string.scan_photos)) },
            )
        }
    }
}

/** Covers the screen while the picker hands over the photos, so it's clear something is happening. */
@Composable
private fun Preparing(count: Int) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
            // Swallows taps, so nothing else starts meanwhile
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 3.dp) {
            Row(
                Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                Text(pluralStringResource(Res.plurals.scan_preparing, count, count), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * A dot on each plant found; tapping one identifies that plant. Dots stay readable on any
 * background and don't pretend to outline the plant exactly, which the detector can't do. The
 * plant the camera is held on gets a ring that fills until it's identified; the picked one is green.
 */
@Composable
private fun Objects(state: ScanState, onAction: (ScanAction) -> Unit) {
    if (state.mode != ScanMode.PICK_PLANT || state.objects.isEmpty()) return
    val (aspect, fill) = when (val source = state.source) {
        ScanSource.Camera -> (state.frameAspect ?: return) to true
        is ScanSource.Photo -> (source.aspect ?: return) to false
        is ScanSource.Photos -> return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val view = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        val target = 48.dp
        state.objects.forEachIndexed { index, shown ->
            val center = shown.region.placeIn(view, aspect, fill).center
            val picked = shown.id == state.picked?.id
            val label = stringResource(Res.string.cd_plant, index + 1)
            with(density) {
                Box(
                    Modifier
                        .offset(center.x.toDp() - target / 2, center.y.toDp() - target / 2)
                        .size(target)
                        .clip(CircleShape)
                        .clickable { onAction(ScanAction.SelectObject(shown.id)) }
                        .semantics { contentDescription = label; role = Role.Button; selected = picked },
                    contentAlignment = Alignment.Center,
                ) {
                    if (shown.id == state.steadyId) SteadyRing()
                    Box(
                        Modifier
                            .size(if (picked) 28.dp else 20.dp)
                            .shadow(4.dp, CircleShape)
                            .background(if (picked) MaterialTheme.colorScheme.primary else Color.White, CircleShape)
                            .border(if (picked) 4.dp else 2.dp, if (picked) Color.White else Color.Black.copy(alpha = 0.25f), CircleShape),
                    )
                }
            }
        }
    }
}

/** Fills over [ScanViewModel.STEADY] while the camera stays on a plant. */
@Composable
private fun SteadyRing() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(ScanViewModel.STEADY.inWholeMilliseconds.toInt(), easing = LinearEasing)) }
    CircularProgressIndicator(
        progress = { progress.value },
        modifier = Modifier.size(40.dp),
        color = Color.White,
        trackColor = Color.Black.copy(alpha = 0.3f),
        strokeWidth = 4.dp,
    )
}

@Composable
private fun ResultsPanel(state: ScanState, onAction: (ScanAction) -> Unit, onOpenSpecies: (Long) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large, tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val picked = state.picked
            when {
                state.isWorking -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(Res.string.scan_working))
                }
                state.mode == ScanMode.PICK_PLANT && picked != null -> Picked(picked, state.source == ScanSource.Camera, onAction, onOpenSpecies)
                state.mode == ScanMode.WHOLE_VIEW && state.results.isNotEmpty() -> state.results.forEach { ResultRow(it, onOpenSpecies) }
                else -> Text(
                    hintFor(state),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                )
            }
            NoticeLine({ onAction(ScanAction.ShowNotice) })
        }
    }
}

/** The plant that was picked, as the model saw it, and what it may be. */
@Composable
private fun Picked(picked: PickedPlant, fromCamera: Boolean, onAction: (ScanAction) -> Unit, onOpenSpecies: (Long) -> Unit) {
    val crop = remember(picked) { picked.image.toImageBitmap() }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        if (crop != null) {
            Image(
                crop,
                contentDescription = stringResource(Res.string.cd_picked_plant),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(88.dp).clip(MaterialTheme.shapes.medium),
            )
        }
        Column(Modifier.weight(1f).heightIn(min = 88.dp), verticalArrangement = Arrangement.Center) {
            if (picked.herbs.isEmpty()) {
                Text(stringResource(Res.string.scan_plant_unknown), style = MaterialTheme.typography.bodyMedium)
            } else {
                picked.herbs.forEachIndexed { i, herb ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    CompactResult(herb, onOpenSpecies)
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (fromCamera) "" else stringResource(Res.string.scan_tap_plant),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (fromCamera) {
            FilledTonalButton(onClick = { onAction(ScanAction.ClosePicked) }) {
                Icon(Icons.Filled.CameraAlt, null, Modifier.size(18.dp))
                Text(stringResource(Res.string.scan_again), Modifier.padding(start = 8.dp))
            }
        } else {
            IconButton(onClick = { onAction(ScanAction.ClosePicked) }) {
                Icon(Icons.Filled.Close, stringResource(Res.string.cd_close))
            }
        }
    }
}

@Composable
private fun hintFor(state: ScanState): String = when {
    state.hasError -> stringResource(Res.string.scan_photo_failed)
    state.identifyFailed -> stringResource(Res.string.scan_identify_failed)
    state.source is ScanSource.Photo && state.mode == ScanMode.PICK_PLANT ->
        stringResource(if (state.objects.isEmpty()) Res.string.scan_photo_none_pick else Res.string.scan_tap_plant)
    state.source is ScanSource.Photo -> stringResource(Res.string.scan_photo_none)
    state.mode == ScanMode.PICK_PLANT && state.objects.isEmpty() -> stringResource(Res.string.scan_looking)
    state.mode == ScanMode.PICK_PLANT -> stringResource(Res.string.scan_hold_steady)
    else -> stringResource(Res.string.scan_point)
}

@Composable
private fun ResultRow(herb: RecognizedHerb, onOpenSpecies: (Long) -> Unit) {
    val species = herb.species
    HerbCard(
        title = AnnotatedString(species?.displayName(LocalVietnameseFirst.current) ?: stringResource(Res.string.scan_unknown_species, herb.label)),
        scientificName = species?.scientificName.orEmpty(),
        supporting = species?.otherName(LocalVietnameseFirst.current),
        onClick = { species?.let { onOpenSpecies(it.id) } },
        trailing = { ConfidenceChip(herb.confidence) },
    )
}

/** Several photos, each in its own card with its most likely herbs. */
@Composable
private fun Batch(
    source: ScanSource.Photos,
    onPickPhotos: () -> Unit,
    onBackToCamera: () -> Unit,
    onShowNotice: () -> Unit,
    onOpenSpecies: (Long) -> Unit,
) {
    val spacing = HerbLensTheme.spacing
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = spacing.maxContentWidth).fillMaxWidth().statusBarsPadding(),
            contentPadding = PaddingValues(start = spacing.lg, end = spacing.lg, top = spacing.lg, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item("notice") { NoticeLine(onShowNotice) }
            itemsIndexed(source.items, key = { index, item -> "$index:${item.uri}" }) { index, item ->
                BatchCard(index, item, onOpenSpecies)
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(spacing.lg), horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            ExtendedFloatingActionButton(onClick = onBackToCamera, icon = { Icon(Icons.Filled.CameraAlt, null) }, text = { Text(stringResource(Res.string.scan_back_to_camera)) })
            ExtendedFloatingActionButton(onClick = onPickPhotos, icon = { Icon(Icons.Filled.PhotoLibrary, null) }, text = { Text(stringResource(Res.string.scan_photos)) })
        }
    }
}

@Composable
private fun BatchCard(index: Int, item: BatchItem, onOpenSpecies: (Long) -> Unit) {
    val spacing = HerbLensTheme.spacing
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(spacing.md), horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            RemoteImage(
                item.uri,
                stringResource(Res.string.cd_plant, index + 1),
                Modifier.size(88.dp).clip(MaterialTheme.shapes.medium),
            )
            Column(Modifier.weight(1f).heightIn(min = 88.dp), verticalArrangement = Arrangement.Center) {
                val herbs = item.herbs
                when {
                    item.failed -> Text(stringResource(Res.string.scan_photo_failed), style = MaterialTheme.typography.bodyMedium)
                    herbs == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(Res.string.scan_working), style = MaterialTheme.typography.bodyMedium)
                    }
                    herbs.isEmpty() -> Text(stringResource(Res.string.scan_photo_none), style = MaterialTheme.typography.bodyMedium)
                    else -> herbs.forEachIndexed { i, herb ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        CompactResult(herb, onOpenSpecies)
                    }
                }
            }
        }
    }
}

/** One possible herb on a line: name, scientific name and confidence. */
@Composable
private fun CompactResult(herb: RecognizedHerb, onOpenSpecies: (Long) -> Unit) {
    val species = herb.species
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = species != null) { species?.let { onOpenSpecies(it.id) } }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                species?.displayName(LocalVietnameseFirst.current) ?: stringResource(Res.string.scan_unknown_species, herb.label),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (species != null) {
                ScientificName(
                    species.scientificName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        ConfidenceChip(herb.confidence)
    }
}
