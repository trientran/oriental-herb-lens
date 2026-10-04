package com.uri.lee.dl.core.common.text

import platform.Foundation.NSString
import platform.Foundation.precomposedStringWithCanonicalMapping

actual object PlatformTextNormalizer : TextNormalizer {
    @Suppress("CAST_NEVER_SUCCEEDS") // Kotlin strings bridge to NSString
    actual override fun toNfc(text: String): String = (text as NSString).precomposedStringWithCanonicalMapping
}
