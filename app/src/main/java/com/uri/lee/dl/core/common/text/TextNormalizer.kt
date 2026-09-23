package com.uri.lee.dl.core.common.text

/**
 * Unicode normalisation. Vietnamese text reaches the app both precomposed (NFC) and decomposed
 * (NFD) — the two look identical but compare unequal — so everything is stored as NFC.
 * The platform supplies the implementation; common Kotlin has no normaliser.
 */
fun interface TextNormalizer {
    fun toNfc(text: String): String
}
