package com.uri.lee.dl.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NoAccounts
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uri.lee.dl.core.designsystem.LegalLinks
import com.uri.lee.dl.core.designsystem.component.SectionCard
import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.resources.privacy_policy
import com.uri.lee.dl.core.designsystem.resources.profile_about
import com.uri.lee.dl.core.designsystem.resources.profile_app
import com.uri.lee.dl.core.designsystem.resources.profile_cite
import com.uri.lee.dl.core.designsystem.resources.profile_cite_body
import com.uri.lee.dl.core.designsystem.resources.profile_cite_copied
import com.uri.lee.dl.core.designsystem.resources.profile_cite_copy
import com.uri.lee.dl.core.designsystem.resources.profile_cite_open
import com.uri.lee.dl.core.designsystem.resources.profile_contact
import com.uri.lee.dl.core.designsystem.resources.profile_delete_account
import com.uri.lee.dl.core.designsystem.resources.profile_full_list
import com.uri.lee.dl.core.designsystem.resources.profile_identification
import com.uri.lee.dl.core.designsystem.resources.profile_language
import com.uri.lee.dl.core.designsystem.resources.profile_min_confidence
import com.uri.lee.dl.core.designsystem.resources.profile_min_confidence_body
import com.uri.lee.dl.core.designsystem.resources.profile_privacy
import com.uri.lee.dl.core.designsystem.resources.profile_share
import com.uri.lee.dl.core.designsystem.resources.profile_sign_in
import com.uri.lee.dl.core.designsystem.resources.profile_sign_in_body
import com.uri.lee.dl.core.designsystem.resources.profile_sign_out
import com.uri.lee.dl.core.designsystem.resources.profile_signed_in
import com.uri.lee.dl.core.designsystem.resources.profile_usage_statistics
import com.uri.lee.dl.core.designsystem.resources.profile_usage_statistics_body
import com.uri.lee.dl.core.designsystem.resources.profile_version
import com.uri.lee.dl.core.designsystem.theme.HerbLensTheme
import com.uri.lee.dl.domain.model.Citation
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Platform features the profile can offer; a null entry hides its row. */
data class ProfileActions(
    val onSignIn: () -> Unit,
    val onShareApp: (() -> Unit)? = null,
    val onOpenLanguageSettings: (() -> Unit)? = null,
    /** Opens account deletion; null hides it. */
    val onDeleteAccount: (() -> Unit)? = null,
    /** Developer and research tools, by name (not translated). */
    val debugTools: List<Pair<String, () -> Unit>> = emptyList(),
)

@Composable
fun ProfileRoute(actions: ProfileActions, modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<ProfileViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileScreen(state, viewModel::onAction, actions, modifier)
}

@Composable
fun ProfileScreen(state: ProfileState, onAction: (ProfileAction) -> Unit, actions: ProfileActions, modifier: Modifier = Modifier) {
    val spacing = HerbLensTheme.spacing
    val uriHandler = LocalUriHandler.current
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = spacing.maxContentWidth).fillMaxWidth().verticalScroll(rememberScrollState()).padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            AccountCard(state.isSignedIn, onSignIn = actions.onSignIn, onSignOut = { onAction(ProfileAction.SignOut) }, onDeleteAccount = actions.onDeleteAccount)

            SectionCard(stringResource(Res.string.profile_identification)) {
                ConfidenceSetting(state.scanSettings.minConfidence) { onAction(ProfileAction.SetMinConfidence(it)) }
            }

            SectionCard(stringResource(Res.string.profile_privacy)) {
                UsageStatisticsSetting(state.usageStatistics) { onAction(ProfileAction.SetUsageStatistics(it)) }
            }

            SectionCard(stringResource(Res.string.profile_app)) {
                actions.onOpenLanguageSettings?.let { LinkRow(Icons.Filled.Language, stringResource(Res.string.profile_language), onClick = it) }
                LinkRow(Icons.AutoMirrored.Filled.List, stringResource(Res.string.profile_full_list)) { uriHandler.openUri(HERB_LIST_URL) }
                actions.onShareApp?.let { LinkRow(Icons.Filled.Share, stringResource(Res.string.profile_share), onClick = it) }
                // The addresses too: without a mail app set up, a mailto link does nothing. One email to both,
                // the university's first
                LinkRow(Icons.Filled.Email, stringResource(Res.string.profile_contact), supporting = CONTACT_EMAILS.joinToString("\n")) {
                    uriHandler.openUri("mailto:" + CONTACT_EMAILS.joinToString(","))
                }
                // In Vietnamese for an app shown in Vietnamese; the legal pages stay English only
                val about = if (Locale.current.language == "vi") LegalLinks.ABOUT_VI else LegalLinks.ABOUT
                LinkRow(Icons.Filled.Info, stringResource(Res.string.profile_about)) { uriHandler.openUri(about) }
                LinkRow(Icons.Filled.Policy, stringResource(Res.string.privacy_policy)) { uriHandler.openUri(LegalLinks.PRIVACY_POLICY) }
                actions.debugTools.forEach { (name, run) -> LinkRow(Icons.Filled.Build, name, onClick = run) }
            }

            if (state.citations.isNotEmpty()) {
                SectionCard(stringResource(Res.string.profile_cite)) {
                    Text(stringResource(Res.string.profile_cite_body), style = MaterialTheme.typography.bodyMedium)
                    state.citations.forEachIndexed { index, citation -> CitationItem(citation) { onAction(ProfileAction.CitationCopied(index)) } }
                }
            }

            Text(
                stringResource(Res.string.profile_version, state.versionName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun AccountCard(isSignedIn: Boolean?, onSignIn: () -> Unit, onSignOut: () -> Unit, onDeleteAccount: (() -> Unit)?) {
    val spacing = HerbLensTheme.spacing
    SectionCard(stringResource(if (isSignedIn == true) Res.string.profile_signed_in else Res.string.profile_sign_in)) {
        when (isSignedIn) {
            true -> Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                TextButton(onClick = onSignOut) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Text(stringResource(Res.string.profile_sign_out), Modifier.padding(start = spacing.sm))
                }
                if (onDeleteAccount != null) {
                    TextButton(onClick = onDeleteAccount, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Filled.NoAccounts, contentDescription = null)
                        Text(stringResource(Res.string.profile_delete_account), Modifier.padding(start = spacing.sm))
                    }
                }
            }
            false -> {
                Text(stringResource(Res.string.profile_sign_in_body), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onSignIn) {
                    Icon(Icons.Filled.AccountCircle, contentDescription = null)
                    Text(stringResource(Res.string.profile_sign_in), Modifier.padding(start = spacing.sm))
                }
            }
            null -> Unit
        }
    }
}

/** Saved when the finger lifts, not on every step of the drag. */
@Composable
private fun ConfidenceSetting(value: Float, onChange: (Float) -> Unit) {
    var dragging by remember(value) { mutableFloatStateOf(value) }
    Column {
        Text(stringResource(Res.string.profile_min_confidence, (dragging * 100).roundToInt()), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(Res.string.profile_min_confidence_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = dragging,
            onValueChange = { dragging = it },
            onValueChangeFinished = { onChange(dragging) },
            valueRange = ProfileViewModel.MIN_CONFIDENCE..ProfileViewModel.MAX_CONFIDENCE,
            steps = 12,
        )
    }
}

@Composable
private fun UsageStatisticsSetting(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HerbLensTheme.spacing.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.profile_usage_statistics), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(Res.string.profile_usage_statistics_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = null)
    }
}

/** The reference is selectable too, for copying part of it. */
@Suppress("DEPRECATION") // LocalClipboard needs a platform ClipEntry; plain text is all that's copied here
@Composable
private fun CitationItem(citation: Citation, onCopied: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    var copied by remember(citation) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.small)
            .padding(start = HerbLensTheme.spacing.md, top = HerbLensTheme.spacing.md, end = HerbLensTheme.spacing.md),
    ) {
        SelectionContainer { Text(citation.text, style = MaterialTheme.typography.bodyMedium) }
        Row(Modifier.align(Alignment.End)) {
            citation.url?.let { url ->
                TextButton(onClick = { uriHandler.openUri(url) }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Text(stringResource(Res.string.profile_cite_open), Modifier.padding(start = HerbLensTheme.spacing.sm))
                }
            }
            TextButton(onClick = { clipboard.setText(AnnotatedString(citation.text)); copied = true; onCopied() }) {
                Icon(if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy, contentDescription = null)
                Text(
                    stringResource(if (copied) Res.string.profile_cite_copied else Res.string.profile_cite_copy),
                    Modifier.padding(start = HerbLensTheme.spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, label: String, supporting: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable(onClick = onClick).semantics { role = Role.Button },
    )
}

private const val HERB_LIST_URL = "https://docs.google.com/spreadsheets/d/16IpEYlpkd7NW3XHXUvhdhJf8LySuhVRLooA7c1SAzOs/edit?usp=sharing"
private val CONTACT_EMAILS = listOf("ttran72@myune.edu.au", "tptrien@gmail.com")
