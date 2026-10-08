package com.uri.lee.dl.feature.herbdetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uri.lee.dl.core.designsystem.component.DialogLayer
import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.resources.cancel
import com.uri.lee.dl.core.designsystem.resources.generic_error
import com.uri.lee.dl.core.designsystem.resources.ok
import com.uri.lee.dl.core.designsystem.resources.profile_sign_in
import com.uri.lee.dl.core.designsystem.resources.suggest_name_body
import com.uri.lee.dl.core.designsystem.resources.suggest_name_language
import com.uri.lee.dl.core.designsystem.resources.suggest_name_label
import com.uri.lee.dl.core.designsystem.resources.suggest_name_listed
import com.uri.lee.dl.core.designsystem.resources.suggest_name_sent
import com.uri.lee.dl.core.designsystem.resources.suggest_name_sign_in
import com.uri.lee.dl.core.designsystem.resources.suggest_name_submit
import com.uri.lee.dl.core.designsystem.resources.suggest_name_title
import com.uri.lee.dl.core.designsystem.theme.HerbLensTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SuggestNameSheet(
    herbId: Long,
    listed: Map<String, List<String>>,
    language: String,
    onSignIn: () -> Unit,
    onDismiss: () -> Unit,
) {
    val viewModel = koinViewModel<SuggestNameViewModel>(key = "suggest-$herbId") { parametersOf(herbId, listed, language) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The view model outlives the sheet (it's kept per herb): each opening starts afresh after a send
    LaunchedEffect(viewModel) { viewModel.onAction(SuggestNameAction.Opened) }
    DialogLayer {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            SuggestNameContent(state, viewModel::onAction, onSignIn, onDismiss)
        }
    }
}

@Composable
internal fun SuggestNameContent(
    state: SuggestNameState,
    onAction: (SuggestNameAction) -> Unit,
    onSignIn: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = HerbLensTheme.spacing
    Column(
        Modifier.fillMaxWidth().padding(horizontal = spacing.xl).padding(bottom = spacing.xl).imePadding(),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(stringResource(Res.string.suggest_name_title), style = MaterialTheme.typography.titleLarge)
        when {
            state.isSubmitted -> {
                Text(stringResource(Res.string.suggest_name_sent), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(Res.string.ok)) }
            }
            !state.isSignedIn -> {
                Text(stringResource(Res.string.suggest_name_sign_in), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onSignIn, modifier = Modifier.align(Alignment.End)) { Text(stringResource(Res.string.profile_sign_in)) }
            }
            else -> {
                Text(
                    stringResource(Res.string.suggest_name_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(Res.string.suggest_name_language), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    state.languages.forEach { code ->
                        FilterChip(
                            selected = code == state.language,
                            onClick = { onAction(SuggestNameAction.LanguageChanged(code)) },
                            label = { Text(languageName(code)) },
                        )
                    }
                }
                val listed = state.listedInLanguage
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = { onAction(SuggestNameAction.DraftChanged(it)) },
                    label = { Text(stringResource(Res.string.suggest_name_label, languageName(state.language))) },
                    singleLine = true,
                    isError = state.hasError,
                    supportingText = when {
                        state.hasError -> {
                            { Text(stringResource(Res.string.generic_error)) }
                        }
                        // What's there already, so it isn't suggested again
                        listed.isNotEmpty() -> {
                            { Text(stringResource(Res.string.suggest_name_listed, listed.joinToString(" · "))) }
                        }
                        else -> null
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onAction(SuggestNameAction.Submit) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
                    Button(onClick = { onAction(SuggestNameAction.Submit) }, enabled = state.canSubmit) {
                        Text(stringResource(Res.string.suggest_name_submit))
                    }
                }
            }
        }
    }
}
