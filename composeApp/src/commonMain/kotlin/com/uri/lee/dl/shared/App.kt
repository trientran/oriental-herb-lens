package com.uri.lee.dl.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.window.core.layout.WindowSizeClass
import com.uri.lee.dl.core.common.AppInfo
import com.uri.lee.dl.core.designsystem.LocalVietnameseFirst
import com.uri.lee.dl.core.designsystem.component.DialogLayer
import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.resources.cancel
import com.uri.lee.dl.core.designsystem.resources.nav_browse
import com.uri.lee.dl.core.designsystem.resources.nav_identify
import com.uri.lee.dl.core.designsystem.resources.nav_profile
import com.uri.lee.dl.core.designsystem.resources.nav_saved
import com.uri.lee.dl.core.designsystem.resources.nav_train
import com.uri.lee.dl.core.designsystem.resources.ok
import com.uri.lee.dl.core.designsystem.resources.profile_language
import com.uri.lee.dl.core.designsystem.resources.status_later
import com.uri.lee.dl.core.designsystem.resources.status_suspended
import com.uri.lee.dl.core.designsystem.resources.status_update
import com.uri.lee.dl.core.designsystem.resources.status_update_recommended
import com.uri.lee.dl.core.designsystem.resources.status_update_required
import com.uri.lee.dl.core.designsystem.theme.HerbLensTheme
import com.uri.lee.dl.domain.analytics.Analytics
import com.uri.lee.dl.domain.ml.ImageEmbedderLoader
import com.uri.lee.dl.domain.ml.PhotoReader
import com.uri.lee.dl.domain.model.NamePreference
import com.uri.lee.dl.domain.model.UpdatePolicy
import com.uri.lee.dl.domain.training.Backbones
import com.uri.lee.dl.feature.auth.DeleteAccountRoute
import com.uri.lee.dl.feature.auth.SignInRoute
import com.uri.lee.dl.feature.browse.BrowseRoute
import com.uri.lee.dl.feature.contribute.ContributePlatform
import com.uri.lee.dl.feature.contribute.ContributeRoute
import com.uri.lee.dl.feature.herbdetails.HerbDetailsRoute
import com.uri.lee.dl.feature.profile.ProfileActions
import com.uri.lee.dl.feature.profile.ProfileRoute
import com.uri.lee.dl.feature.saved.SavedRoute
import com.uri.lee.dl.feature.scan.CameraPreview
import com.uri.lee.dl.feature.scan.ScanRoute
import com.uri.lee.dl.feature.training.TrainingPlatform
import com.uri.lee.dl.feature.training.TrainingRoute
import com.uri.lee.dl.shared.research.ResearchController
import com.uri.lee.dl.shared.research.ResearchDialog
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Serializable data object IdentifyDestination
@Serializable data class BrowseDestination(val openHerbId: Long? = null)
@Serializable data object SavedDestination
@Serializable data object TrainDestination
@Serializable data object ProfileDestination
@Serializable data object SignInDestination
@Serializable data object DeleteAccountDestination
@Serializable data class ContributeDestination(val herbId: Long)
@Serializable data class SpeciesDestination(val herbId: Long)

private enum class TopLevel(val route: Any, val routeClass: KClass<*>, val icon: ImageVector, val label: StringResource) {
    IDENTIFY(IdentifyDestination, IdentifyDestination::class, Icons.Filled.CameraAlt, Res.string.nav_identify),
    BROWSE(BrowseDestination(), BrowseDestination::class, Icons.Filled.Search, Res.string.nav_browse),
    SAVED(SavedDestination, SavedDestination::class, Icons.Filled.Bookmarks, Res.string.nav_saved),
    TRAIN(TrainDestination, TrainDestination::class, Icons.Filled.Psychology, Res.string.nav_train),
    PROFILE(ProfileDestination, ProfileDestination::class, Icons.Filled.Person, Res.string.nav_profile),
}

/**
 * The whole app. Four destinations: a bottom bar on phones, a rail on wider windows. Browse and
 * Saved show the species beside the list when there's room.
 *
 * @param openHerbId a species to show, e.g. one picked on a scan screen; null shows nothing extra.
 */
@Composable
fun App(actions: PlatformActions, openHerbId: Long? = null) {
    val vietnameseFirst = koinInject<NamePreference>().vietnameseFirst()
    HerbLensTheme {
        // Right to left for Arabic everywhere: Android does it itself, the web and iOS follow this
        val direction = if (Locale.current.language in RTL_LANGUAGES) LayoutDirection.Rtl else LocalLayoutDirection.current
        CompositionLocalProvider(LocalVietnameseFirst provides vietnameseFirst, LocalLayoutDirection provides direction) {
            AppContent(actions, openHerbId)
        }
    }
}

@Composable
private fun AppContent(actions: PlatformActions, openHerbId: Long?) {
    val navController = rememberNavController()
    val appInfo = koinInject<AppInfo>()
    val isDebug = appInfo.isDebug
    var showBenchmark by remember { mutableStateOf(false) }
    var showResearch by remember { mutableStateOf(false) }
    var showLanguages by remember { mutableStateOf(false) }
    LaunchedEffect(openHerbId) {
        if (openHerbId != null) navController.navigateTopLevel(BrowseDestination(openHerbId))
    }
    val current = navController.currentBackStackEntryAsState().value?.destination
    val analytics = koinInject<Analytics>()
    val screen = current?.route?.let(::screenName)
    LaunchedEffect(screen) { screen?.let(analytics::screen) }
    // A rail from 600dp wide (tablets, foldables, phones in landscape); the bottom bar below
    // that; none on full-screen flows (sign-in, contributing)
    val wide = currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val fullScreen = listOf(SignInDestination::class, DeleteAccountDestination::class, ContributeDestination::class, SpeciesDestination::class).any { current?.hasRoute(it) == true }
    val signIn = { navController.navigate(SignInDestination) { launchSingleTop = true } }
    val addPhotos = if (actions.sharePhotos) { herbId: Long -> navController.navigate(ContributeDestination(herbId)) } else null
    NavigationSuiteScaffold(
        layoutType = when {
            fullScreen -> NavigationSuiteType.None
            wide -> NavigationSuiteType.NavigationRail
            else -> NavigationSuiteType.NavigationBar
        },
        navigationSuiteItems = {
            TopLevel.entries.forEach { item ->
                item(
                    selected = current?.hasRoute(item.routeClass) == true,
                    onClick = { navController.navigateTopLevel(item.route) },
                    icon = { Icon(item.icon, contentDescription = null) },
                    // One line in every language: five tabs leave little room on a phone
                    label = { Text(stringResource(item.label), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                )
            }
        },
    ) {
        NavHost(navController, startDestination = BrowseDestination(), modifier = Modifier.fillMaxSize()) {
            composable<IdentifyDestination> {
                ScanRoute(pickPhotos = actions.pickPhotos, onOpenSpecies = { navController.navigate(SpeciesDestination(it)) })
            }
            composable<SpeciesDestination> { entry ->
                HerbDetailsRoute(
                    herbId = entry.toRoute<SpeciesDestination>().herbId,
                    onBack = { navController.popBackStack() },
                    onAddPhotos = addPhotos,
                    onSignIn = signIn,
                )
            }
            composable<BrowseDestination> { entry ->
                SpeciesListDetail(entry.toRoute<BrowseDestination>().openHerbId, addPhotos, signIn) { selected, open ->
                    BrowseRoute(onOpenSpecies = open, selectedId = selected, onVoiceSearch = actions.onVoiceSearch, modifier = Modifier.statusBarsPadding())
                }
            }
            composable<SavedDestination> {
                SpeciesListDetail(null, addPhotos, signIn) { selected, open ->
                    SavedRoute(onOpenSpecies = open, selectedId = selected, modifier = Modifier.statusBarsPadding())
                }
            }
            composable<TrainDestination> {
                val files = actions.files
                TrainingRoute(
                    TrainingPlatform(
                        pickPhotos = actions.pickPhotos,
                        pickDatasetFolder = files?.pickDatasetFolder,
                        pickDatasetZip = files?.pickDatasetZip,
                        pickModelFile = files?.pickModelFile,
                        saveFile = files?.saveFile ?: { _, _ -> },
                        camera = { onFrame, modifier -> CameraPreview(onFrame, modifier) },
                    ),
                    modifier = Modifier.statusBarsPadding(),
                )
            }
            composable<ProfileDestination> {
                ProfileRoute(
                    ProfileActions(
                        onSignIn = signIn,
                        onShareApp = actions.onShareApp,
                        onOpenLanguageSettings = actions.onOpenLanguageSettings ?: actions.languages?.let { { showLanguages = true } },
                        onDeleteAccount = { navController.navigate(DeleteAccountDestination) { launchSingleTop = true } },
                        debugTools = listOfNotNull(
                            actions.trainingBenchmark?.takeIf { isDebug }?.let { "Training benchmark (Phase 7)" to { showBenchmark = true } },
                            actions.research?.let { "Research mode" to { showResearch = true } },
                        ),
                    ),
                    modifier = Modifier.statusBarsPadding(),
                )
            }
            composable<SignInDestination> {
                SignInRoute(actions.requestGoogleSignIn, onDone = { navController.popBackStack() }, requestAppleSignIn = actions.requestAppleSignIn)
            }
            composable<DeleteAccountDestination> {
                DeleteAccountRoute(
                    requestGoogleSignIn = actions.requestGoogleSignIn,
                    onDone = { navController.popBackStack() },
                    requestAppleSignIn = actions.requestAppleSignIn,
                    revokeAppleToken = actions.revokeAppleToken,
                )
            }
            composable<ContributeDestination> { entry ->
                ContributeRoute(
                    herbId = entry.toRoute<ContributeDestination>().herbId,
                    platform = ContributePlatform(actions.pickPhotos, actions.currentLocation, actions.requestNotificationPermission),
                    onSignIn = signIn,
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }
    StatusDialogs(actions)
    val benchmarkSources = actions.trainingBenchmark
    if (showBenchmark && benchmarkSources != null) {
        val benchmark = TrainingBenchmark(koinInject(), koinInject(), getKoin().getOrNull())
        TrainingBenchmarkDialog(benchmark, benchmarkSources) { showBenchmark = false }
    }
    val languages = actions.languages
    if (showLanguages && languages != null) {
        DialogLayer { LanguageDialog(languages) { showLanguages = false } }
    }
    val research = actions.research
    if (showResearch && research != null) {
        val reader = koinInject<PhotoReader>()
        val embedders = koinInject<ImageEmbedderLoader>()
        val backbones = koinInject<Backbones>()
        // One for the app: a run carries on when the screen closes
        val controller = remember { ResearchController.shared(research(), reader, embedders, backbones, appInfo.versionName) }
        DialogLayer { ResearchDialog(controller) { showResearch = false } }
    }
}

/** Switching tabs keeps each tab's own state (scroll position, open species). */
private fun NavHostController.navigateTopLevel(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = route !is BrowseDestination || route.openHerbId == null
}

@Composable
private fun StatusDialogs(actions: PlatformActions) {
    val viewModel = koinViewModel<AppViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val noDismiss = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    DialogLayer {
        when {
            state.status.isSuspended -> AlertDialog(
                onDismissRequest = {},
                properties = noDismiss,
                text = { Text(stringResource(Res.string.status_suspended)) },
                confirmButton = { TextButton(onClick = actions.onExit) { Text(stringResource(Res.string.ok)) } },
            )
            state.showUpdate -> {
                val required = state.status.update == UpdatePolicy.REQUIRED
                AlertDialog(
                    onDismissRequest = { if (!required) viewModel.onAction(AppAction.DismissUpdate) },
                    properties = if (required) noDismiss else DialogProperties(),
                    text = { Text(stringResource(if (required) Res.string.status_update_required else Res.string.status_update_recommended)) },
                    confirmButton = { TextButton(onClick = actions.onOpenStore) { Text(stringResource(Res.string.status_update)) } },
                    dismissButton = if (required) null else {
                        { TextButton(onClick = { viewModel.onAction(AppAction.DismissUpdate) }) { Text(stringResource(Res.string.status_later)) } }
                    },
                )
            }
        }
    }
}

/** "com.uri.lee.dl.shared.SpeciesDestination/{herbId}" → "species", for usage statistics. */
internal fun screenName(route: String): String =
    route.substringBefore('/').substringBefore('?').substringAfterLast('.').removeSuffix("Destination")
        .replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()

/** Languages written right to left; of the app's, only Arabic. */
private val RTL_LANGUAGES = setOf("ar", "fa", "he", "ur")

/** The web's language picker: each language by its own name. */
@Composable
private fun LanguageDialog(languages: LanguageChoice, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.profile_language)) },
        text = {
            Column {
                languages.options.forEach { (code, name) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { if (code != languages.current) languages.choose(code) else onDismiss() }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = code == languages.current, onClick = null)
                        Text(name, Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
