package com.uri.lee.dl.core.common.text

/**
 * Unicode normalisation. Vietnamese text reaches the app both precomposed (NFC) and decomposed
 * (NFD) — the two look identical but compare unequal — so everything is stored as NFC.
 * Common Kotlin has no normaliser, so [PlatformTextNormalizer] uses each platform's own.
 */
fun interface TextNormalizer {
    fun toNfc(text: String): String
}

expect object PlatformTextNormalizer : TextNormalizer {
    override fun toNfc(text: String): String
}
