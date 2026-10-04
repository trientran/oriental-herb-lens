package com.uri.lee.dl.data.platform

import android.content.Context
import android.net.Uri
import com.uri.lee.dl.Utils.compressToJpgByteArray
import com.uri.lee.dl.domain.media.ImageCompressor
import com.uri.lee.dl.domain.media.LocalImage

/** A photo picked through the system picker. */
data class UriImage(val contentUri: Uri) : LocalImage {
    override val uri: String get() = contentUri.toString()
}

/** 600 px on the longer side at 70 % JPEG quality, as uploads have always been. */
class AndroidImageCompressor(private val context: Context) : ImageCompressor {
    override suspend fun compress(image: LocalImage): ByteArray? =
        context.compressToJpgByteArray((image as UriImage).contentUri, maxImageDimension = 600)
}
