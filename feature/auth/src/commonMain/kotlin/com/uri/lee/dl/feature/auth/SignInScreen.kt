package com.uri.lee.dl.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.resources.cd_close
import com.uri.lee.dl.core.designsystem.resources.sign_in_body
import com.uri.lee.dl.core.designsystem.resources.sign_in_error
import com.uri.lee.dl.core.designsystem.resources.sign_in_google
import com.uri.lee.dl.core.designsystem.resources.privacy_policy
import com.uri.lee.dl.core.designsystem.resources.terms_of_service
import com.uri.lee.dl.core.designsystem.LegalLinks
import com.uri.lee.dl.core.designsystem.resources.sign_in_title
import com.uri.lee.dl.core.designsystem.theme.HerbLensTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Asks the platform for a Google ID token (Credential Manager on Android); null means the user
 * backed out. Throws when no account can be offered.
 */
typealias GoogleIdTokenRequest = suspend () -> String?

/** Sign-in, needed only to contribute (photos, names). Closes itself once signed in. */
@Composable
fun SignInRoute(requestGoogleIdToken: GoogleIdTokenRequest, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<SignInViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.isSignedIn) { if (state.isSignedIn) onDone() }
    SignInScreen(
        state = state,
        onGoogle = {
            viewModel.onAction(SignInAction.GoogleStarted)
            scope.launch {
                val action = try {
                    SignInAction.GoogleFinished(requestGoogleIdToken())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    SignInAction.GoogleFailed
                }
                viewModel.onAction(action)
            }
        },
        onClose = onDone,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(state: SignInState, onGoogle: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = HerbLensTheme.spacing
    val uriHandler = LocalUriHandler.current
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.cd_close)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 440.dp).fillMaxWidth().padding(spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                Icon(Icons.Filled.Spa, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(Res.string.sign_in_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(
                    stringResource(Res.string.sign_in_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onGoogle, enabled = !state.isWorking, modifier = Modifier.fillMaxWidth()) {
                    if (state.isWorking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(Res.string.sign_in_google))
                }
                if (state.hasError) {
                    Text(stringResource(Res.string.sign_in_error), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
                Row {
                    TextButton(onClick = { uriHandler.openUri(LegalLinks.PRIVACY_POLICY) }) {
                        Text(stringResource(Res.string.privacy_policy), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { uriHandler.openUri(LegalLinks.TERMS_OF_SERVICE) }) {
                        Text(stringResource(Res.string.terms_of_service), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
