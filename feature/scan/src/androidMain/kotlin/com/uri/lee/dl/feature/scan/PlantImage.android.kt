package com.uri.lee.dl.feature.scan

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.uri.lee.dl.core.ml.MlKitClassifierImage
import com.uri.lee.dl.domain.ml.ClassifierImage

internal actual fun ClassifierImage.toImageBitmap(): ImageBitmap? = (this as? MlKitClassifierImage)?.bitmap?.asImageBitmap()
