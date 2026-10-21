package com.uri.lee.dl.core.designsystem

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether species are named in Vietnamese first (otherwise English): for readers using Vietnamese
 * or in Vietnam. Decided once by the app (see App.kt); Vietnamese names are always shown too.
 */
val LocalVietnameseFirst = staticCompositionLocalOf { false }

/**
 * A language's name for menus, from its ISO 639-1 code: on Android and iOS its own name ("Français"), on the
 * web its name in the page's language, as the browser canvas has fonts for that language's script only.
 */
val LocalLanguageName = staticCompositionLocalOf<(String) -> String> { { it } }
