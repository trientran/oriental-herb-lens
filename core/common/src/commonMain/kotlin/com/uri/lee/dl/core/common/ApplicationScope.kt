package com.uri.lee.dl.core.common

import kotlinx.coroutines.CoroutineScope

/**
 * Lives as long as the process. For work that must outlive a screen (an upload started from a
 * ViewModel, a content download) but not the app; replaces the old global `globalScope`.
 */
class ApplicationScope(scope: CoroutineScope) : CoroutineScope by scope
