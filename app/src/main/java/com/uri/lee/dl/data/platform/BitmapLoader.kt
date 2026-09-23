package com.uri.lee.dl.data.platform

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.uri.lee.dl.Utils.loadBitmapFromUri

/** Decodes a picked image, downsampled and rotated upright; lets ViewModels avoid holding a Context. */
class BitmapLoader(private val context: Context) {
    suspend fun load(uri: Uri, maxDimension: Int): Bitmap? = context.loadBitmapFromUri(uri, maxDimension)
}
