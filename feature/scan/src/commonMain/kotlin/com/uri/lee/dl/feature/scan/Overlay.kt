package com.uri.lee.dl.feature.scan

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.uri.lee.dl.domain.ml.Region

/**
 * Where [region] of an image with [aspect] (width / height) appears in a view of [view] size,
 * when the image is scaled to fill the view ([fill], cropping the overflow) or to fit inside it.
 */
internal fun Region.placeIn(view: Size, aspect: Float, fill: Boolean): Rect {
    val scale = if (fill) maxOf(view.width / aspect, view.height) else minOf(view.width / aspect, view.height)
    val shownWidth = aspect * scale
    val shownHeight = scale
    val offsetX = (view.width - shownWidth) / 2
    val offsetY = (view.height - shownHeight) / 2
    return Rect(
        left = offsetX + left * shownWidth,
        top = offsetY + top * shownHeight,
        right = offsetX + right * shownWidth,
        bottom = offsetY + bottom * shownHeight,
    )
}
