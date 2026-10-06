package com.uri.lee.dl.core.designsystem

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether species are named in Vietnamese first (otherwise English): for readers using Vietnamese
 * or in Vietnam. Decided once by the app (see App.kt); Vietnamese names are always shown too.
 */
val LocalVietnameseFirst = staticCompositionLocalOf { false }
