package com.uri.lee.dl.feature.scan

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.uri.lee.dl.core.ml.IosClassifierImage
import com.uri.lee.dl.domain.ml.ClassifierImage
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
internal actual fun ClassifierImage.toImageBitmap(): ImageBitmap? {
    val image = (this as? IosClassifierImage)?.image ?: return null
    val data = UIImageJPEGRepresentation(image, 0.9) ?: return null
    val bytes = ByteArray(data.length.toInt()).apply { usePinned { memcpy(it.addressOf(0), data.bytes, data.length) } }
    return Image.makeFromEncoded(bytes).toComposeImageBitmap()
}
