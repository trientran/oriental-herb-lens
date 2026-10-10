package com.uri.lee.dl.core.designsystem

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether this platform shares photos of species: the apps do, the web doesn't. Screens that
 * mention sharing photos word themselves by it. Set once by the app (see App.kt).
 */
val LocalSharesPhotos = staticCompositionLocalOf { true }
