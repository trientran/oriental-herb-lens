package com.uri.lee.dl.feature.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uri.lee.dl.core.designsystem.LocalVietnameseFirst
import com.uri.lee.dl.core.designsystem.component.EmptyState
import com.uri.lee.dl.core.designsystem.component.ErrorState
import com.uri.lee.dl.core.designsystem.component.HerbCard
import com.uri.lee.dl.core.designsystem.component.LoadingState
import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.resources.favorites_empty_body
import com.uri.lee.dl.core.designsystem.resources.favorites_empty_title
import com.uri.lee.dl.core.designsystem.resources.generic_error
import com.uri.lee.dl.core.designsystem.resources.history_empty_body
import com.uri.lee.dl.core.designsystem.resources.history_empty_title
import com.uri.lee.dl.core.designsystem.resources.saved_favorites
import com.uri.lee.dl.core.designsystem.resources.saved_history
import com.uri.lee.dl.core.designsystem.theme.HerbLensTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SavedRoute(onOpenSpecies: (Long) -> Unit, selectedId: Long?, modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<SavedViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    SavedScreen(state, viewModel::onAction, onOpenSpecies, selectedId, modifier)
}

@Composable
fun SavedScreen(
    state: SavedState,
    onAction: (SavedAction) -> Unit,
    onOpenSpecies: (Long) -> Unit,
    selectedId: Long?,
    modifier: Modifier = Modifier,
) {
    val spacing = HerbLensTheme.spacing
    Column(modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
            SavedTab.entries.forEach { tab ->
                Tab(
                    selected = state.tab == tab,
                    onClick = { onAction(SavedAction.SelectTab(tab)) },
                    text = {
                        Text(stringResource(if (tab == SavedTab.FAVORITES) Res.string.saved_favorites else Res.string.saved_history))
                    },
                )
            }
        }
        val shown = state.shown
        when {
            state.hasError -> ErrorState(stringResource(Res.string.generic_error))
            shown == null -> LoadingState()
            shown.isEmpty() -> if (state.tab == SavedTab.FAVORITES) {
                EmptyState(
                    icon = Icons.Filled.FavoriteBorder,
                    title = stringResource(Res.string.favorites_empty_title),
                    body = stringResource(Res.string.favorites_empty_body),
                )
            } else {
                EmptyState(
                    icon = Icons.Filled.History,
                    title = stringResource(Res.string.history_empty_title),
                    body = stringResource(Res.string.history_empty_body),
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                items(shown, key = { it.id }) { species ->
                    HerbCard(
                        title = AnnotatedString(species.displayName(LocalVietnameseFirst.current)),
                        scientificName = species.scientificName,
                        supporting = species.otherName(LocalVietnameseFirst.current) ?: species.family,
                        onClick = { onOpenSpecies(species.id) },
                        selected = species.id == selectedId,
                    )
                }
            }
        }
    }
}
