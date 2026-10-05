package com.uri.lee.dl.core.common.text

actual object PlatformTextNormalizer : TextNormalizer {
    actual override fun toNfc(text: String): String = normalizeNfc(text)
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun normalizeNfc(text: String): String = js("text.normalize('NFC')")
