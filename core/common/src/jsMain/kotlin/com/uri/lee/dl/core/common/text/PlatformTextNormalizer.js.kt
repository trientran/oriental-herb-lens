package com.uri.lee.dl.core.common.text

actual object PlatformTextNormalizer : TextNormalizer {
    actual override fun toNfc(text: String): String = text.asDynamic().normalize("NFC") as String
}
