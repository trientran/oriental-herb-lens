package com.uri.lee.dl.data.platform

import com.uri.lee.dl.core.common.text.TextNormalizer
import java.text.Normalizer

object JvmTextNormalizer : TextNormalizer {
    override fun toNfc(text: String): String =
        if (Normalizer.isNormalized(text, Normalizer.Form.NFC)) text
        else Normalizer.normalize(text, Normalizer.Form.NFC)
}
