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
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.w3c.fetch.Response

private const val FONTS = "https://cdn.jsdelivr.net/fontsource/fonts"

/**
 * The Noto font (Open Font License) for scripts the app's own font, Be Vietnam Pro, doesn't cover, for the
 * page's language only. The browser canvas has no system fonts to fall back on, so without it such text would
 * show as empty boxes. Android and iOS fall back on their system fonts and need none of this.
 */
private fun fallbackFontFor(language: String): Pair<String, String>? = when (language) {
    "ar", "fa" -> "Noto Sans Arabic" to "$FONTS/noto-sans-arabic@5.2.5/arabic-400-normal.ttf"
    "zh" -> "Noto Sans SC" to "$FONTS/noto-sans-sc@5.2.5/chinese-simplified-400-normal.ttf"
    "ja" -> "Noto Sans JP" to "$FONTS/noto-sans-jp@5.2.5/japanese-400-normal.ttf"
    "ko" -> "Noto Sans KR" to "$FONTS/noto-sans-kr@5.2.5/korean-400-normal.ttf"
    "hi", "mr", "ne" -> "Noto Sans Devanagari" to "$FONTS/noto-sans-devanagari@5.2.5/devanagari-400-normal.ttf"
    "bn" -> "Noto Sans Bengali" to "$FONTS/noto-sans-bengali@5.2.5/bengali-400-normal.ttf"
    "si" -> "Noto Sans Sinhala" to "$FONTS/noto-sans-sinhala@5.2.5/sinhala-400-normal.ttf"
    "th" -> "Noto Sans Thai" to "$FONTS/noto-sans-thai@5.2.5/thai-400-normal.ttf"
    "el" -> "Noto Sans Greek" to "$FONTS/noto-sans@5.2.5/greek-400-normal.ttf"
    "ru", "uk", "bg", "sr" -> "Noto Sans Cyrillic" to "$FONTS/noto-sans@5.2.5/cyrillic-400-normal.ttf"
    // Azerbaijani's ə and Esperanto's ĉ ĝ ĥ ĵ ŝ ŭ, besides the accents Be Vietnam Pro has
    "az", "eo" -> "Noto Sans Latin Extended" to "$FONTS/noto-sans@5.2.5/latin-ext-400-normal.ttf"
    else -> null
}

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

/**
 * A language's name in the page's language, from the browser (Intl.DisplayNames), so the language menus are
 * readable without every script's font: "Arabic" in English, "Tiếng Ả Rập" in Vietnamese.
 */
internal fun webLanguageName(code: String): String = runCatching {
    val names = js("new Intl.DisplayNames([navigator.language], { type: 'language' })")
    (names.of(code) as String?)?.replaceFirstChar { it.uppercase() }
}.getOrNull() ?: code
