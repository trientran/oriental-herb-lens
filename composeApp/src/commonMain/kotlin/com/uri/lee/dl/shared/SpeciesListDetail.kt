package com.uri.lee.dl.shared

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.uri.lee.dl.core.designsystem.component.EmptyState
import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.resources.app_name
import com.uri.lee.dl.feature.herbdetails.HerbDetailsRoute
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * A list of species with the selected one's details: side by side on wide windows, one after
 * the other on phones, where back returns to the list.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun SpeciesListDetail(
    initialSelection: Long?,
    onAddPhotos: (Long) -> Unit,
    onSignIn: () -> Unit,
    list: @Composable (selectedId: Long?, open: (Long) -> Unit) -> Unit,
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<Long>()
    val scope = rememberCoroutineScope()
    val open: (Long) -> Unit = { id -> scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id) } }
    LaunchedEffect(initialSelection) { initialSelection?.let(open) }

    val selected = navigator.currentDestination?.contentKey
    val singlePane = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Hidden ||
        navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Hidden

    // System back (Android back gesture, iOS edge swipe) closes the species on narrow windows
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = navigator.canNavigateBack(),
        onBackCompleted = { scope.launch { navigator.navigateBack() } },
    )
    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        // The scaffold focuses a pane as it appears. A plain focus target first in each pane takes
        // that focus, so the search field doesn't, and the keyboard stays down.
        listPane = { AnimatedPane { Box(Modifier.focusable()) { list(selected, open) } } },
        detailPane = {
            AnimatedPane(Modifier.focusable()) {
                if (selected == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(icon = Icons.Filled.Spa, title = stringResource(Res.string.app_name))
                    }
                } else {
                    HerbDetailsRoute(
                        herbId = selected,
                        onBack = if (singlePane) ({ scope.launch { navigator.navigateBack() } }) else null,
                        onAddPhotos = onAddPhotos,
                        onSignIn = onSignIn,
                    )
                }
            }
        },
    )
}
