package com.uri.lee.dl.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NoAccounts
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.resources.cd_back
import com.uri.lee.dl.core.designsystem.resources.delete_account_body
import com.uri.lee.dl.core.designsystem.resources.delete_account_confirm
import com.uri.lee.dl.core.designsystem.resources.delete_account_confirm_apple
import com.uri.lee.dl.core.designsystem.resources.delete_account_confirm_google
import com.uri.lee.dl.core.designsystem.resources.delete_account_failed
import com.uri.lee.dl.core.designsystem.resources.delete_account_title
import com.uri.lee.dl.core.designsystem.theme.HerbLensTheme
import com.uri.lee.dl.domain.repository.SignInProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Deleting the account, which App Store guideline 5.1.1(v) requires of apps with sign-in. The
 * user confirms by signing in again with the same provider; then everything is deleted.
 *
 * @param revokeAppleToken tells Apple the app no longer uses the account (iOS); null elsewhere.
 */
@Composable
fun DeleteAccountRoute(
    requestGoogleSignIn: GoogleSignInRequest,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    requestAppleSignIn: AppleSignInRequest? = null,
    revokeAppleToken: (suspend (authorizationCode: String) -> Unit)? = null,
) {
    val viewModel = koinViewModel<DeleteAccountViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.isDeleted) { if (state.isDeleted) onDone() }
    DeleteAccountScreen(
        state = state,
        onConfirm = {
            viewModel.onAction(DeleteAccountAction.Started)
            scope.launch {
                val action = try {
                    DeleteAccountAction.Confirmed(
                        when (state.provider) {
                            SignInProvider.GOOGLE -> requestGoogleSignIn()?.let(Proof::Google)
                            SignInProvider.APPLE -> requestAppleSignIn?.let { request -> request()?.let { Proof.Apple(it, revokeAppleToken) } } ?: Proof.None
                            SignInProvider.OTHER, null -> Proof.None
                        },
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.withTag("DeleteAccount").e(e) { "Confirming sign-in failed" }
                    DeleteAccountAction.Failed
                }
                viewModel.onAction(action)
            }
        },
        onBack = onDone,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteAccountScreen(state: DeleteAccountState, onConfirm: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = HerbLensTheme.spacing
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                Icon(Icons.Filled.NoAccounts, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                Text(stringResource(Res.string.delete_account_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(stringResource(Res.string.delete_account_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = onConfirm,
                    enabled = !state.isWorking && state.provider != null,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isWorking) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onError)
                    } else {
                        Text(
                            stringResource(
                                when (state.provider) {
                                    SignInProvider.GOOGLE -> Res.string.delete_account_confirm_google
                                    SignInProvider.APPLE -> Res.string.delete_account_confirm_apple
                                    else -> Res.string.delete_account_confirm
                                },
                            ),
                        )
                    }
                }
                if (state.hasError) {
                    Text(stringResource(Res.string.delete_account_failed), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
