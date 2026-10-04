package com.uri.lee.dl.core.common.text

import java.text.Normalizer

actual object PlatformTextNormalizer : TextNormalizer {
    actual override fun toNfc(text: String): String =
        if (Normalizer.isNormalized(text, Normalizer.Form.NFC)) text
        else Normalizer.normalize(text, Normalizer.Form.NFC)
}
