package com.uri.lee.dl.feature.herbdetails

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uri.lee.dl.core.designsystem.LegalLinks
import com.uri.lee.dl.core.designsystem.LocalVietnameseFirst
import com.uri.lee.dl.core.designsystem.component.DialogLayer
import com.uri.lee.dl.core.designsystem.component.EmptyState
import com.uri.lee.dl.core.designsystem.component.ErrorState
import com.uri.lee.dl.core.designsystem.component.LoadingState
import com.uri.lee.dl.core.designsystem.component.RemoteImage
import com.uri.lee.dl.core.designsystem.component.ScientificName
import com.uri.lee.dl.core.designsystem.component.SectionCard
import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.resources.cd_add_favorite
import com.uri.lee.dl.core.designsystem.resources.cd_back
import com.uri.lee.dl.core.designsystem.resources.cd_close
import com.uri.lee.dl.core.designsystem.resources.cd_photo
import com.uri.lee.dl.core.designsystem.resources.cd_remove_favorite
import com.uri.lee.dl.core.designsystem.resources.details_add_photos
import com.uri.lee.dl.core.designsystem.resources.details_classification
import com.uri.lee.dl.core.designsystem.resources.details_english_names
import com.uri.lee.dl.core.designsystem.resources.details_family
import com.uri.lee.dl.core.designsystem.resources.details_full
import com.uri.lee.dl.core.designsystem.resources.details_genus
import com.uri.lee.dl.core.designsystem.resources.details_map_hint
import com.uri.lee.dl.core.designsystem.resources.details_map_note
import com.uri.lee.dl.core.designsystem.resources.details_names
import com.uri.lee.dl.core.designsystem.resources.details_names_unverified
import com.uri.lee.dl.core.designsystem.resources.details_names_unverified_body
import com.uri.lee.dl.core.designsystem.resources.details_no_photos
import com.uri.lee.dl.core.designsystem.resources.details_not_found
import com.uri.lee.dl.core.designsystem.resources.details_photo_map
import com.uri.lee.dl.core.designsystem.resources.details_photos_loading
import com.uri.lee.dl.core.designsystem.resources.details_place_photo
import com.uri.lee.dl.core.designsystem.resources.details_place_photos
import com.uri.lee.dl.core.designsystem.resources.details_share_in_apps
import com.uri.lee.dl.core.designsystem.resources.details_suggest_name
import com.uri.lee.dl.core.designsystem.resources.details_vietnamese_names
import com.uri.lee.dl.core.designsystem.resources.details_view_on_gbif
import com.uri.lee.dl.core.designsystem.resources.generic_error
import com.uri.lee.dl.core.designsystem.resources.notice_contributor_hidden
import com.uri.lee.dl.core.designsystem.resources.notice_failed
import com.uri.lee.dl.core.designsystem.resources.notice_reported
import com.uri.lee.dl.core.designsystem.resources.ok
import com.uri.lee.dl.core.designsystem.resources.photo_counter
import com.uri.lee.dl.core.designsystem.resources.retry
import com.uri.lee.dl.core.designsystem.theme.HerbLensTheme
import com.uri.lee.dl.core.maps.HerbMap
import com.uri.lee.dl.core.maps.LatLng
import com.uri.lee.dl.domain.model.GeoLocation
import com.uri.lee.dl.domain.model.NameLanguages
import com.uri.lee.dl.domain.model.Species
import com.uri.lee.dl.domain.model.SpeciesPhoto
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * @param onBack null when the screen sits beside the list (wide windows) and needs no back button.
 * @param onAddPhotos opens the contribution flow; null hides it (a platform without one yet).
 */
@Composable
fun HerbDetailsRoute(
    herbId: Long,
    onBack: (() -> Unit)?,
    onAddPhotos: ((Long) -> Unit)?,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = koinViewModel<HerbDetailsViewModel>(key = "herb-$herbId") { parametersOf(herbId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var suggesting by rememberSaveable(herbId) { mutableStateOf(false) }

    HerbDetailsScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        onAddPhotos = onAddPhotos?.let { { it(herbId) } },
        onSuggestName = { suggesting = true },
        modifier = modifier,
    )
    val species = state.species
    if (suggesting && species != null) {
        SuggestNameSheet(
            herbId = herbId,
            listed = NameLanguages.ENABLED.associateWith { species.names(it) },
            language = if (LocalVietnameseFirst.current) NameLanguages.VIETNAMESE else NameLanguages.ENGLISH,
            onSignIn = { suggesting = false; onSignIn() },
            onDismiss = { suggesting = false },
        )
    }
    state.viewingPhoto?.let { index ->
        PhotoViewer(
            photos = state.photos,
            startIndex = index.coerceIn(0, (state.photos.size - 1).coerceAtLeast(0)),
            speciesName = species?.displayName(LocalVietnameseFirst.current).orEmpty(),
            onDismiss = { viewModel.onAction(HerbDetailsAction.ViewPhoto(null)) },
            onReport = { photo, reason -> viewModel.onAction(HerbDetailsAction.Report(photo, reason)) },
            onHideContributor = { viewModel.onAction(HerbDetailsAction.HideContributor(it)) },
            onSignInToReport = if (state.isSignedIn) null else {
                {
                    viewModel.onAction(HerbDetailsAction.ViewPhoto(null))
                    onSignIn()
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HerbDetailsScreen(
    state: HerbDetailsState,
    onAction: (HerbDetailsAction) -> Unit,
    onBack: (() -> Unit)?,
    onAddPhotos: (() -> Unit)?,
    onSuggestName: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val notice = state.notice
    val message = notice?.let {
        stringResource(
            when (it) {
                ModerationNotice.REPORTED -> Res.string.notice_reported
                ModerationNotice.CONTRIBUTOR_HIDDEN -> Res.string.notice_contributor_hidden
                ModerationNotice.FAILED -> Res.string.notice_failed
            },
        )
    }
    LaunchedEffect(notice) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onAction(HerbDetailsAction.NoticeShown)
        }
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.species?.displayName(LocalVietnameseFirst.current).orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back))
                        }
                    }
                },
                actions = {
                    if (state.species != null) {
                        IconButton(onClick = { onAction(HerbDetailsAction.ToggleFavorite) }) {
                            if (state.isFavorite) {
                                Icon(
                                    Icons.Filled.Favorite,
                                    contentDescription = stringResource(Res.string.cd_remove_favorite),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Icon(Icons.Filled.FavoriteBorder, contentDescription = stringResource(Res.string.cd_add_favorite))
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val species = state.species
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when {
                state.hasError -> ErrorState(
                    stringResource(Res.string.generic_error),
                    retryLabel = stringResource(Res.string.retry),
                    onRetry = { onAction(HerbDetailsAction.Retry) },
                )
                state.notFound -> EmptyState(Icons.Filled.SearchOff, stringResource(Res.string.details_not_found))
                species == null -> LoadingState()
                else -> DetailsContent(species, state.photos, state.photosLoading, state.place, onAction, onAddPhotos, onSuggestName)
            }
        }
    }
}

@Composable
private fun DetailsContent(
    species: Species,
    photos: List<SpeciesPhoto>,
    photosLoading: Boolean,
    place: SelectedPlace?,
    onAction: (HerbDetailsAction) -> Unit,
    onAddPhotos: (() -> Unit)?,
    onSuggestName: () -> Unit,
) {
    val spacing = HerbLensTheme.spacing
    val uriHandler = LocalUriHandler.current
    var showInfo by rememberSaveable(species.id) { mutableStateOf(false) }
    if (showInfo) {
        SpeciesInfoSheet(
            species,
            onOpenGbif = {
                onAction(HerbDetailsAction.GbifOpened)
                uriHandler.openUri("https://www.gbif.org/species/${species.id}")
            },
            onDismiss = { showInfo = false },
        )
    }
    LazyColumn(
        modifier = Modifier.widthIn(max = spacing.maxContentWidth).fillMaxWidth(),
        contentPadding = PaddingValues(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item("photos") {
            val name = species.displayName(LocalVietnameseFirst.current)
            when {
                photos.isNotEmpty() -> PhotoCarousel(photos, name) { onAction(HerbDetailsAction.ViewPhoto(it)) }
                photosLoading -> PhotosLoading()
                else -> NoPhotos(onAddPhotos)
            }
        }
        // Names and classification can be selected and copied, e.g. to search elsewhere
        item("title") {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    val commonName = species.displayName(LocalVietnameseFirst.current).takeIf { it != species.scientificName }
                    commonName?.let { Text(it, style = MaterialTheme.typography.headlineMedium) }
                    ScientificName(
                        species.scientificName,
                        authorship = species.authorship,
                        style = if (commonName == null) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item("actions") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                if (onAddPhotos != null) {
                    FilledTonalButton(onClick = onAddPhotos) {
                        Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                        Text(stringResource(Res.string.details_add_photos), Modifier.padding(start = spacing.sm))
                    }
                }
                OutlinedButton(onClick = onSuggestName) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Text(stringResource(Res.string.details_suggest_name), Modifier.padding(start = spacing.sm))
                }
            }
        }
        // Where photos can't be shared (the web), where they can
        if (onAddPhotos == null) item("share-in-apps") { ShareInApps() }
        // Every common name in the languages the app shows, with a warning that nobody checked them
        if (NameLanguages.ENABLED.any { species.names(it).isNotEmpty() }) {
            item("names") {
                SectionCard(stringResource(Res.string.details_names), titleAction = { UnverifiedNames() }) {
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            NameLanguages.ENABLED.forEach { language -> NameList(languageName(language), species.names(language)) }
                        }
                    }
                }
            }
        }
        val photoPlaces = photos.mapNotNull { photo -> photo.location?.let { LatLng(it.latitude, it.longitude) } }.distinct()
        if (photoPlaces.isNotEmpty()) {
            item("map") {
                SectionCard(stringResource(Res.string.details_photo_map)) {
                    HerbMap(
                        photoPlaces,
                        Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.medium),
                        onPointClick = { index ->
                            val point = photoPlaces[index]
                            onAction(HerbDetailsAction.SelectPlace(GeoLocation(point.latitude, point.longitude)))
                        },
                    )
                    if (place != null) {
                        PlaceCard(place, photos, onOpen = { onAction(HerbDetailsAction.ViewPhoto(it)) }, onClose = { onAction(HerbDetailsAction.SelectPlace(null)) })
                    } else {
                        Text(
                            stringResource(Res.string.details_map_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Places come from contributors' phones or pins, and nobody checks them
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Icon(Icons.Outlined.Info, contentDescription = null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            stringResource(Res.string.details_map_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item("classification") {
            SectionCard(stringResource(Res.string.details_classification)) {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        if (species.family.isNotBlank()) LabeledValue(stringResource(Res.string.details_family), species.family)
                        if (species.genus.isNotBlank()) LabeledValue(stringResource(Res.string.details_genus), species.genus, italic = true)
                    }
                }
                // The full record stays in the app; GBIF is one step further, from the sheet
                TextButton(onClick = { showInfo = true; onAction(HerbDetailsAction.InfoOpened) }) {
                    Text(stringResource(Res.string.details_full))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, Modifier.padding(start = spacing.xs))
                }
            }
        }
    }
}

/** A pin's photos and address; tapping it opens the first of its photos. */
@Composable
private fun PlaceCard(place: SelectedPlace, photos: List<SpeciesPhoto>, onOpen: (Int) -> Unit, onClose: () -> Unit) {
    val spacing = HerbLensTheme.spacing
    val here = photos.withIndex().filter { (_, photo) -> photo.location == place.location }
    val first = here.firstOrNull() ?: return
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onOpen(first.index) }
            .padding(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        RemoteImage(
            first.value.thumbnailUrl,
            stringResource(Res.string.details_place_photo),
            Modifier.size(64.dp).clip(MaterialTheme.shapes.small),
        )
        Column(Modifier.weight(1f)) {
            Text(
                place.address ?: formatCoordinates(place.location),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                pluralStringResource(Res.plurals.details_place_photos, here.size, here.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.cd_close)) }
    }
}

/** "10.7769, 106.7009": a place without an address yet. */
private fun formatCoordinates(location: GeoLocation): String {
    fun four(value: Double) = (kotlin.math.round(value * 10_000) / 10_000).toString()
    return "${four(location.latitude)}, ${four(location.longitude)}"
}

@Composable
private fun NameList(label: String, names: List<String>) {
    if (names.isEmpty()) return
    LabeledValue(label, names.joinToString(" · "))
}

/** A language's name, from its ISO 639-1 code. */
@Composable
internal fun languageName(code: String): String = when (code) {
    NameLanguages.VIETNAMESE -> stringResource(Res.string.details_vietnamese_names)
    NameLanguages.ENGLISH -> stringResource(Res.string.details_english_names)
    else -> code.uppercase()
}

/** A warning beside "Names": common names come from GBIF and users, and experts haven't checked them. */
@Composable
private fun UnverifiedNames() {
    var open by remember { mutableStateOf(false) }
    val title = stringResource(Res.string.details_names_unverified)
    IconButton(onClick = { open = true }, modifier = Modifier.size(32.dp)) {
        Icon(Icons.Outlined.WarningAmber, contentDescription = title, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
    }
    if (open) {
        DialogLayer {
            AlertDialog(
                onDismissRequest = { open = false },
                icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null) },
                title = { Text(title) },
                text = { Text(stringResource(Res.string.details_names_unverified_body)) },
                confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(Res.string.ok)) } },
            )
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String, italic: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (italic) ScientificName(value, style = MaterialTheme.typography.bodyLarge)
        else Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Swipeable photos (or with the arrows); tapping one opens the full-screen viewer. */
@Composable
private fun PhotoCarousel(photos: List<SpeciesPhoto>, speciesName: String, onOpen: (Int) -> Unit) {
    val pager = rememberPagerState { photos.size }
    val spacing = HerbLensTheme.spacing
    Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(MaterialTheme.shapes.large)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { photos[it].url }) { page ->
            val photo = photos[page]
            RemoteImage(
                photo.thumbnailUrl,
                contentDescription = stringResource(Res.string.cd_photo, speciesName),
                modifier = Modifier.fillMaxSize().clickable { onOpen(page) },
            )
        }
        PagerArrows(pager)
        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            contentColor = Color.White,
            shape = RoundedCornerShape(topEnd = spacing.md),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Column(Modifier.padding(horizontal = spacing.md, vertical = spacing.sm)) {
                Text(stringResource(Res.string.photo_counter, pager.currentPage + 1, photos.size), style = MaterialTheme.typography.labelMedium)
                PhotoCredit(photos[pager.currentPage], MaterialTheme.typography.labelSmall, Color.White, showSourceLink = false)
            }
        }
    }
}

/** The photo strip's place while photos are fetched: a softly pulsing panel of the same size. */
@Composable
private fun PhotosLoading() {
    val pulse by rememberInfiniteTransition().animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
    )
    val description = stringResource(Res.string.details_photos_loading)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(MaterialTheme.shapes.large)
            .graphicsLayer { alpha = pulse }
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Image, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}

/** Points to the apps, which share photos, with links to their store pages. */
@Composable
private fun ShareInApps() {
    val uriHandler = LocalUriHandler.current
    Column {
        Text(
            stringResource(Res.string.details_share_in_apps),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow {
            // Store names, the same in every language
            TextButton(onClick = { uriHandler.openUri(LegalLinks.GOOGLE_PLAY) }) { Text("Google Play") }
            TextButton(onClick = { uriHandler.openUri(LegalLinks.APP_STORE) }) { Text("App Store") }
        }
    }
}

@Composable
private fun NoPhotos(onAddPhotos: (() -> Unit)?) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        EmptyState(
            icon = Icons.Filled.Image,
            title = stringResource(Res.string.details_no_photos),
            actionLabel = onAddPhotos?.let { stringResource(Res.string.details_add_photos) },
            onAction = onAddPhotos,
        )
    }
}
