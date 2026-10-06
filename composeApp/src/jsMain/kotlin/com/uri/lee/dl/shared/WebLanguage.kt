package com.uri.lee.dl.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.platform.Font
import co.touchlab.kermit.Logger
import kotlin.js.Promise
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.w3c.fetch.Response

private const val LANGUAGE_KEY = "herblens.language"

/**
 * The browser follows its own language unless the visitor chose one in Profile: then that's
 * saved here and given to the page as its language before the app starts, so the app's text,
 * dates and layout direction all follow it.
 */
internal fun applySavedLanguage() {
    val saved = runCatching { localStorage.getItem(LANGUAGE_KEY) }.getOrNull() ?: return
    val navigator = window.navigator.asDynamic()
    val language: dynamic = js("({})")
    language.get = { saved }
    val languages: dynamic = js("({})")
    languages.get = { arrayOf(saved) }
    js("Object").defineProperty(navigator, "language", language)
    js("Object").defineProperty(navigator, "languages", languages)
}

/** The language picker's choices: the app's languages, by their own names. */
internal val WEB_LANGUAGES = listOf(
    "en" to "English",
    "vi" to "Tiếng Việt",
    "ar" to "العربية",
    "es" to "Español",
    "fr" to "Français",
    "ru" to "Русский",
    "zh" to "中文",
)

/** Saves the visitor's language and reloads the page in it. */
internal fun chooseWebLanguage(code: String) {
    runCatching { localStorage.setItem(LANGUAGE_KEY, code) }
    window.location.reload()
}

/**
 * Scripts the app's own font doesn't cover, loaded from the Noto fonts (Open Font License) for
 * the page's language only. The browser canvas has no system fonts to fall back on, so without
 * these Arabic, Chinese and Russian text would show as empty boxes.
 */
private fun fallbackFontFor(language: String): Pair<String, String>? = when (language) {
    "ar" -> "Noto Sans Arabic" to "$FONTS/noto-sans-arabic@5.3.0/arabic-400-normal.ttf"
    "zh" -> "Noto Sans SC" to "$FONTS/noto-sans-sc@5.3.0/chinese-simplified-400-normal.ttf"
    "ru" -> "Noto Sans Cyrillic" to "$FONTS/noto-sans@5.3.0/cyrillic-400-normal.ttf"
    else -> null
}

private const val FONTS = "https://cdn.jsdelivr.net/fontsource/fonts"

/** Shows [content] once the language's fallback font is ready (straight away if it needs none, or the font can't load). */
@Composable
internal fun WithFallbackFonts(content: @Composable () -> Unit) {
    val font = remember { fallbackFontFor(Locale.current.language) }
    var ready by remember { mutableStateOf(font == null) }
    val resolver = LocalFontFamilyResolver.current
    LaunchedEffect(font) {
        if (font == null) return@LaunchedEffect
        runCatching {
            val response = window.asDynamic().fetch(font.second).unsafeCast<Promise<Response>>().await()
            check(response.ok) { "HTTP ${response.status}" }
            val bytes = Int8Array(response.arrayBuffer().await().unsafeCast<ArrayBuffer>()).unsafeCast<ByteArray>()
            // Preloaded fonts are what text falls back on for characters other fonts lack
            resolver.preload(FontFamily(Font(font.first, bytes)))
        }.onFailure { Logger.withTag("Fonts").w(it) { "Couldn't load ${font.first}" } }
        ready = true
    }
    if (ready) content()
}
