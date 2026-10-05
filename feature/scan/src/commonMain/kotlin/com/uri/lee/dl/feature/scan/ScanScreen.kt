package com.uri.lee.dl.feature.scan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.uri.lee.dl.core.designsystem.component.ConfidenceChip
import com.uri.lee.dl.core.designsystem.component.HerbCard
import com.uri.lee.dl.core.designsystem.component.RemoteImage
import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.resources.cd_plant
import com.uri.lee.dl.core.designsystem.resources.scan_back_to_camera
import com.uri.lee.dl.core.designsystem.resources.scan_looking
import com.uri.lee.dl.core.designsystem.resources.scan_not_recognised
import com.uri.lee.dl.core.designsystem.resources.scan_photo_failed
import com.uri.lee.dl.core.designsystem.resources.scan_photo_none
import com.uri.lee.dl.core.designsystem.resources.scan_photo_none_pick
import com.uri.lee.dl.core.designsystem.resources.scan_photos
import com.uri.lee.dl.core.designsystem.resources.scan_pick_plant
import com.uri.lee.dl.core.designsystem.resources.scan_point
import com.uri.lee.dl.core.designsystem.resources.scan_tap_plant
import com.uri.lee.dl.core.designsystem.resources.scan_unknown_species
import com.uri.lee.dl.core.designsystem.resources.scan_whole_view
import com.uri.lee.dl.core.designsystem.resources.scan_working
import com.uri.lee.dl.core.designsystem.theme.HerbLensTheme
import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.domain.model.RecognizedHerb
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Identify a herb: the camera fills the screen, or a picked photo. Results show in a panel at the
 * bottom (beside the image on wide windows); tapping one opens the species.
 */
@Composable
fun ScanRoute(
    pickPhotos: ((List<LocalImage>) -> Unit) -> Unit,
    onOpenSpecies: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = koinViewModel<ScanViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScanScreen(
        state = state,
        onAction = viewModel::onAction,
        onPickPhotos = { pickPhotos { viewModel.onAction(ScanAction.PhotosPicked(it)) } },
        onOpenSpecies = onOpenSpecies,
        modifier = modifier,
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
    camera: @Composable () -> Unit = {},
) {
    val wide = currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
    val source = state.source
    if (source is ScanSource.Photos) {
        Batch(source, onPickPhotos, { onAction(ScanAction.BackToCamera) }, onOpenSpecies, modifier)
        return
    }
    Row(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight().background(Color.Black)) {
            when (source) {
                ScanSource.Camera -> camera()
                is ScanSource.Photo -> RemoteImage(source.uri, null, Modifier.fillMaxSize(), ContentScale.Fit, showBackground = false)
                is ScanSource.Photos -> Unit
            }
            Objects(state, onAction)
            ModeSwitch(state.mode, { onAction(ScanAction.SetMode(it)) }, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp))
            Actions(source, onPickPhotos, { onAction(ScanAction.BackToCamera) }, Modifier.align(if (wide) Alignment.BottomCenter else Alignment.TopEnd))
            if (!wide) {
                ResultsPanel(state, onOpenSpecies, Modifier.align(Alignment.BottomCenter).padding(12.dp).widthIn(max = 560.dp).fillMaxWidth())
            }
        }
        if (wide) {
            Surface(Modifier.width(380.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
                ResultsPanel(state, onOpenSpecies, Modifier.statusBarsPadding().padding(16.dp))
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

/** Boxes over the plants found; tapping one identifies it. The selected one is highlighted. */
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
        state.objects.forEachIndexed { index, shown ->
            val rect = shown.region.placeIn(view, aspect, fill)
            val selected = shown.id == state.selectedId
            val label = stringResource(Res.string.cd_plant, index + 1)
            with(density) {
                Box(
                    Modifier
                        .offset(rect.left.toDp(), rect.top.toDp())
                        .size(rect.width.toDp(), rect.height.toDp())
                        .border(
                            BorderStroke(if (selected) 4.dp else 2.dp, if (selected) MaterialTheme.colorScheme.tertiaryContainer else Color.White.copy(alpha = 0.8f)),
                            RoundedCornerShape(16.dp),
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onAction(ScanAction.SelectObject(shown.id)) }
                        .semantics { contentDescription = label; role = Role.Button },
                )
            }
        }
    }
}

@Composable
private fun ResultsPanel(state: ScanState, onOpenSpecies: (Long) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large, tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val hint = hintFor(state)
            if (state.isWorking) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(Res.string.scan_working))
                }
            } else if (state.results.isEmpty()) {
                Text(hint ?: "", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(8.dp))
            } else {
                state.results.forEach { ResultRow(it, onOpenSpecies) }
                if (state.mode == ScanMode.PICK_PLANT && state.objects.size > 1) {
                    Text(
                        stringResource(Res.string.scan_tap_plant),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun hintFor(state: ScanState): String? = when {
    state.hasError -> stringResource(Res.string.scan_photo_failed)
    state.source is ScanSource.Photo && state.mode == ScanMode.PICK_PLANT -> stringResource(Res.string.scan_photo_none_pick)
    state.source is ScanSource.Photo -> stringResource(Res.string.scan_photo_none)
    state.mode == ScanMode.PICK_PLANT && state.objects.isEmpty() -> stringResource(Res.string.scan_looking)
    state.mode == ScanMode.PICK_PLANT -> stringResource(Res.string.scan_not_recognised)
    else -> stringResource(Res.string.scan_point)
}

@Composable
private fun ResultRow(herb: RecognizedHerb, onOpenSpecies: (Long) -> Unit) {
    val species = herb.species
    HerbCard(
        title = AnnotatedString(species?.let { it.preferredVietnameseName ?: it.scientificName } ?: stringResource(Res.string.scan_unknown_species, herb.label)),
        scientificName = species?.scientificName.orEmpty(),
        supporting = species?.preferredEnglishName,
        onClick = { species?.let { onOpenSpecies(it.id) } },
        trailing = { ConfidenceChip(herb.confidence) },
    )
}

/** Several photos, each with its most likely herbs. */
@Composable
private fun Batch(
    source: ScanSource.Photos,
    onPickPhotos: () -> Unit,
    onBackToCamera: () -> Unit,
    onOpenSpecies: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = HerbLensTheme.spacing
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = spacing.maxContentWidth).fillMaxWidth().statusBarsPadding(),
            contentPadding = PaddingValues(start = spacing.lg, end = spacing.lg, top = spacing.lg, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            items(source.items, key = { it.uri }) { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    RemoteImage(item.uri, null, Modifier.width(96.dp).aspectRatio(1f).clip(MaterialTheme.shapes.medium))
                    Column(Modifier.weight(1f).heightIn(min = 96.dp), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        val herbs = item.herbs
                        when {
                            item.failed -> Text(stringResource(Res.string.scan_photo_failed))
                            herbs == null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            herbs.isEmpty() -> Text(stringResource(Res.string.scan_photo_none), style = MaterialTheme.typography.bodyMedium)
                            else -> herbs.forEach { ResultRow(it, onOpenSpecies) }
                        }
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(spacing.lg), horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            ExtendedFloatingActionButton(onClick = onBackToCamera, icon = { Icon(Icons.Filled.CameraAlt, null) }, text = { Text(stringResource(Res.string.scan_back_to_camera)) })
            ExtendedFloatingActionButton(onClick = onPickPhotos, icon = { Icon(Icons.Filled.PhotoLibrary, null) }, text = { Text(stringResource(Res.string.scan_photos)) })
        }
    }
}
