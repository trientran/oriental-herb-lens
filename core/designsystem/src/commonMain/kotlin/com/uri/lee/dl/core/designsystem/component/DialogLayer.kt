package com.uri.lee.dl.core.designsystem.component

import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable

/**
 * Wraps a dialog, sheet or menu. It's laid out separately from the page, so the page's text
 * selection (the web app's) must not reach into it: selecting there would fail.
 */
@Composable
fun DialogLayer(content: @Composable () -> Unit) = DisableSelection(content)
