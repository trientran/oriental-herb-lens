package com.uri.lee.dl.feature.contribute

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uri.lee.dl.core.designsystem.component.EmptyState
import com.uri.lee.dl.core.designsystem.component.RemoteImage
import com.uri.lee.dl.core.designsystem.component.SectionCard
import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.resources.cd_close
import com.uri.lee.dl.core.designsystem.resources.cd_remove_photo
import com.uri.lee.dl.core.designsystem.resources.contribute_add_photos
import com.uri.lee.dl.core.designsystem.resources.contribute_choose_on_map
import com.uri.lee.dl.core.designsystem.resources.contribute_done
import com.uri.lee.dl.core.designsystem.resources.contribute_failed
import com.uri.lee.dl.core.designsystem.resources.contribute_finished
import com.uri.lee.dl.core.designsystem.resources.contribute_finished_partly
import com.uri.lee.dl.core.designsystem.resources.contribute_location
import com.uri.lee.dl.core.designsystem.resources.contribute_location_body
import com.uri.lee.dl.core.designsystem.resources.contribute_location_unavailable
import com.uri.lee.dl.core.designsystem.resources.contribute_photos
import com.uri.lee.dl.core.designsystem.resources.contribute_photos_tip
import com.uri.lee.dl.core.designsystem.resources.contribute_remove_location
import com.uri.lee.dl.core.designsystem.resources.contribute_sign_in
import com.uri.lee.dl.core.designsystem.resources.contribute_title
import com.uri.lee.dl.core.designsystem.resources.contribute_upload
import com.uri.lee.dl.core.designsystem.resources.contribute_upload_photos
import com.uri.lee.dl.core.designsystem.resources.contribute_uploading
import com.uri.lee.dl.core.designsystem.resources.contribute_use_my_location
import com.uri.lee.dl.core.designsystem.resources.details_add_photos
import com.uri.lee.dl.core.designsystem.resources.profile_sign_in
import com.uri.lee.dl.core.designsystem.theme.HerbLensTheme
import com.uri.lee.dl.core.maps.HerbMap
import com.uri.lee.dl.core.maps.LatLng
import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.domain.model.GeoLocation
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** What each platform supplies to the contribute screen. */
data class ContributePlatform(
    /** Opens the system photo picker and reports the photos chosen (none if cancelled). */
    val pickPhotos: ((List<LocalImage>) -> Unit) -> Unit,
    /** Asks for permission if needed and reports where the device is, or null; null hides the option. */
    val currentLocation: (((GeoLocation?) -> Unit) -> Unit)? = null,
)

@Composable
fun ContributeRoute(herbId: Long, platform: ContributePlatform, onSignIn: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<ContributeViewModel>(key = "contribute-$herbId") { parametersOf(herbId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContributeScreen(state, viewModel::onAction, platform, onSignIn, onDone, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContributeScreen(
    state: ContributeState,
    onAction: (ContributeAction) -> Unit,
    platform: ContributePlatform,
    onSignIn: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = HerbLensTheme.spacing
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.speciesName?.let { stringResource(Res.string.contribute_title, it) } ?: stringResource(Res.string.details_add_photos),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.cd_close)) }
                },
            )
        },
        bottomBar = {
            if (state.phase == UploadPhase.Editing || state.phase == UploadPhase.Failed) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { onAction(ContributeAction.Upload) },
                        enabled = state.canUpload,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(spacing.lg),
                    ) {
                        Text(
                            if (state.photos.isEmpty()) stringResource(Res.string.contribute_upload)
                            else pluralStringResource(Res.plurals.contribute_upload_photos, state.photos.size, state.photos.size),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when (val phase = state.phase) {
                is UploadPhase.Uploading -> Uploading(phase)
                is UploadPhase.Finished -> Finished(phase, onDone)
                else -> Editor(state, onAction, platform, onSignIn)
            }
        }
    }
}

@Composable
private fun Editor(state: ContributeState, onAction: (ContributeAction) -> Unit, platform: ContributePlatform, onSignIn: () -> Unit) {
    val spacing = HerbLensTheme.spacing
    val pick = { platform.pickPhotos { onAction(ContributeAction.PhotosPicked(it)) } }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        modifier = Modifier.widthIn(max = spacing.maxContentWidth).fillMaxWidth(),
        contentPadding = PaddingValues(spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        if (!state.isSignedIn) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionCard(stringResource(Res.string.profile_sign_in)) {
                    Text(stringResource(Res.string.contribute_sign_in), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onSignIn) { Text(stringResource(Res.string.profile_sign_in)) }
                }
            }
        }
        if (state.phase == UploadPhase.Failed) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(Res.string.contribute_failed), color = MaterialTheme.colorScheme.error)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(stringResource(Res.string.contribute_photos), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(Res.string.contribute_photos_tip),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.photos, key = { it.uri }) { photo ->
            Box(Modifier.aspectRatio(1f).clip(MaterialTheme.shapes.medium)) {
                RemoteImage(photo.uri, contentDescription = null, modifier = Modifier.fillMaxSize())
                IconButton(
                    onClick = { onAction(ContributeAction.RemovePhoto(photo)) },
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(Res.string.cd_remove_photo),
                            modifier = Modifier.padding(4.dp).size(16.dp),
                        )
                    }
                }
            }
        }
        if (state.photos.size < ContributeViewModel.MAX_PHOTOS) {
            item(key = "add") {
                Card(
                    onClick = pick,
                    modifier = Modifier.aspectRatio(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(spacing.sm),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                        Text(stringResource(Res.string.contribute_add_photos), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            LocationSection(state.location, onAction, platform.currentLocation)
        }
    }
}

@Composable
private fun LocationSection(
    picked: PickedLocation?,
    onAction: (ContributeAction) -> Unit,
    currentLocation: (((GeoLocation?) -> Unit) -> Unit)?,
) {
    val spacing = HerbLensTheme.spacing
    var showMap by rememberSaveable { mutableStateOf(false) }
    var unavailable by rememberSaveable { mutableStateOf(false) }
    SectionCard(stringResource(Res.string.contribute_location), Modifier.padding(top = spacing.md)) {
        Text(stringResource(Res.string.contribute_location_body), style = MaterialTheme.typography.bodyMedium)
        if (picked != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    picked.address ?: "${picked.location.latitude.format()}, ${picked.location.longitude.format()}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (unavailable) {
            Text(stringResource(Res.string.contribute_location_unavailable), color = MaterialTheme.colorScheme.error)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            if (currentLocation != null) {
                FilledTonalButton(onClick = {
                    unavailable = false
                    currentLocation { found ->
                        if (found == null) unavailable = true else onAction(ContributeAction.LocationPicked(found))
                    }
                }) {
                    Icon(Icons.Filled.MyLocation, contentDescription = null)
                    Text(stringResource(Res.string.contribute_use_my_location), Modifier.padding(start = spacing.sm))
                }
            }
            OutlinedButton(onClick = { showMap = true }) {
                Icon(Icons.Filled.Map, contentDescription = null)
                Text(stringResource(Res.string.contribute_choose_on_map), Modifier.padding(start = spacing.sm))
            }
            if (picked != null) {
                TextButton(onClick = { onAction(ContributeAction.ClearLocation) }) { Text(stringResource(Res.string.contribute_remove_location)) }
            }
        }
        if (picked != null) {
            HerbMap(
                points = listOf(LatLng(picked.location.latitude, picked.location.longitude)),
                modifier = Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.medium),
            )
        }
    }
    if (showMap) {
        PlacePicker(
            start = picked?.location,
            onPicked = {
                showMap = false
                onAction(ContributeAction.LocationPicked(it))
            },
            onDismiss = { showMap = false },
        )
    }
}

@Composable
private fun Uploading(phase: UploadPhase.Uploading) {
    val spacing = HerbLensTheme.spacing
    Column(
        Modifier.widthIn(max = 440.dp).fillMaxWidth().padding(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.contribute_uploading, phase.uploaded, phase.total), style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(
            progress = { if (phase.total == 0) 0f else phase.uploaded.toFloat() / phase.total },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Finished(phase: UploadPhase.Finished, onDone: () -> Unit) {
    EmptyState(
        icon = Icons.Filled.CheckCircle,
        title = if (phase.failed == 0) stringResource(Res.string.contribute_finished)
        else stringResource(Res.string.contribute_finished_partly, phase.uploaded, phase.uploaded + phase.failed),
        actionLabel = stringResource(Res.string.contribute_done),
        onAction = onDone,
    )
}

private fun Double.format(): String {
    val scaled = kotlin.math.round(this * 10_000) / 10_000
    return scaled.toString()
}
