package com.uri.lee.dl.feature.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uri.lee.dl.core.designsystem.component.ErrorState
import com.uri.lee.dl.core.designsystem.component.EmptyState
import com.uri.lee.dl.core.designsystem.component.highlighted
import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.resources.browse_header
import com.uri.lee.dl.core.designsystem.resources.cd_clear_search
import com.uri.lee.dl.core.designsystem.resources.cd_voice_search
import com.uri.lee.dl.core.designsystem.resources.generic_error
import com.uri.lee.dl.core.designsystem.resources.retry
import com.uri.lee.dl.core.designsystem.resources.search_hint
import com.uri.lee.dl.core.designsystem.resources.search_no_results
import com.uri.lee.dl.core.designsystem.resources.search_no_results_body
import com.uri.lee.dl.core.designsystem.resources.search_placeholder
import com.uri.lee.dl.core.designsystem.theme.HerbLensTheme
import com.uri.lee.dl.domain.model.Species
import com.uri.lee.dl.domain.search.NameKind
import com.uri.lee.dl.domain.search.SpeciesMatch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Lists are sorted by Vietnamese name for Vietnamese speakers, by scientific name otherwise. */
@Composable
fun BrowseRoute(
    onOpenSpecies: (Long) -> Unit,
    selectedId: Long?,
    modifier: Modifier = Modifier,
    /** Starts platform speech recognition and reports the text; null hides the microphone. */
    onVoiceSearch: (((String) -> Unit) -> Unit)? = null,
) {
    val vietnamese = Locale.current.language == "vi"
    val viewModel = koinViewModel<BrowseViewModel> { parametersOf(vietnamese) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    BrowseScreen(
        state = state,
        onAction = viewModel::onAction,
        onOpenSpecies = onOpenSpecies,
        selectedId = selectedId,
        onVoiceSearch = onVoiceSearch?.let { start -> { start { viewModel.onAction(BrowseAction.QueryChanged(it)) } } },
        modifier = modifier,
    )
}

@Composable
fun BrowseScreen(
    state: BrowseState,
    onAction: (BrowseAction) -> Unit,
    onOpenSpecies: (Long) -> Unit,
    selectedId: Long?,
    modifier: Modifier = Modifier,
    onVoiceSearch: (() -> Unit)? = null,
) {
    val spacing = HerbLensTheme.spacing
    Column(modifier.fillMaxSize()) {
        SearchField(
            query = state.query,
            onQueryChange = { onAction(BrowseAction.QueryChanged(it)) },
            onVoiceSearch = onVoiceSearch,
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.sm),
        )
        when {
            state.isSearching -> SearchResults(state.query, state.results, selectedId, onOpenSpecies)
            state.hasError && state.species.isEmpty() -> ErrorState(
                message = stringResource(Res.string.generic_error),
                retryLabel = stringResource(Res.string.retry),
                onRetry = { onAction(BrowseAction.Retry) },
            )
            else -> CatalogList(state, selectedId, onOpenSpecies, onLoadMore = { onAction(BrowseAction.LoadMore) })
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onVoiceSearch: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        shape = CircleShape,
        label = { Text(stringResource(Res.string.search_hint)) },
        placeholder = { Text(stringResource(Res.string.search_placeholder), maxLines = 1) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            when {
                query.isNotEmpty() -> IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(Res.string.cd_clear_search))
                }
                onVoiceSearch != null -> IconButton(onClick = onVoiceSearch) {
                    Icon(Icons.Filled.Mic, contentDescription = stringResource(Res.string.cd_voice_search))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}

@Composable
private fun CatalogList(
    state: BrowseState,
    selectedId: Long?,
    onOpenSpecies: (Long) -> Unit,
    onLoadMore: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val spacing = HerbLensTheme.spacing
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 10
        }
    }
    LaunchedEffect(nearEnd, state.species.size) { if (nearEnd) onLoadMore() }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = spacing.lg, end = spacing.lg, bottom = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        item(key = "header") {
            Text(
                stringResource(Res.string.browse_header),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = spacing.xs),
            )
        }
        items(state.species, key = { it.id }) { species ->
            SpeciesCard(species, selected = species.id == selectedId, onClick = { onOpenSpecies(species.id) })
        }
        if (state.isLoading) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(spacing.lg), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchResults(query: String, results: List<SpeciesMatch>, selectedId: Long?, onOpenSpecies: (Long) -> Unit) {
    val spacing = HerbLensTheme.spacing
    if (results.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.SearchOff,
            title = stringResource(Res.string.search_no_results, query.trim()),
            body = stringResource(Res.string.search_no_results_body),
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = spacing.lg, end = spacing.lg, bottom = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        items(results, key = { it.species.id }) { match ->
            SpeciesCard(
                species = match.species,
                selected = match.species.id == selectedId,
                onClick = { onOpenSpecies(match.species.id) },
                title = titleFor(match),
                supporting = supportingFor(match.species, match),
            )
        }
    }
}

/** The Vietnamese name, highlighted when that's where the query matched. */
@Composable
private fun titleFor(match: SpeciesMatch) = when (match.kind) {
    NameKind.VIETNAMESE -> highlighted(match.matchedName, match.highlight)
    else -> highlighted(match.species.preferredVietnameseName ?: match.species.scientificName, null)
}

/** Shows which other name matched (a synonym, the English name), so the result makes sense. */
private fun supportingFor(species: Species, match: SpeciesMatch): String? = when {
    match.kind == NameKind.VIETNAMESE && match.matchedName != species.preferredVietnameseName -> match.matchedName
    match.kind == NameKind.ENGLISH -> match.matchedName
    else -> species.preferredEnglishName ?: species.family
}
