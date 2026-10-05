package com.uri.lee.dl.feature.scan

import androidx.compose.ui.graphics.ImageBitmap
import com.uri.lee.dl.domain.ml.ClassifierImage

/** The picked plant's crop, to show next to what it may be; null if it can't be shown. */
internal expect fun ClassifierImage.toImageBitmap(): ImageBitmap?
