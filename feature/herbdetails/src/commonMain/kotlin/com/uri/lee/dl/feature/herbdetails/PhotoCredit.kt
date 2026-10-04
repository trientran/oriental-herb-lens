package com.uri.lee.dl.feature.herbdetails

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.resources.photo_by_user
import com.uri.lee.dl.core.designsystem.resources.photo_credit
import com.uri.lee.dl.core.designsystem.resources.photo_credit_publisher
import com.uri.lee.dl.core.designsystem.resources.photo_source
import com.uri.lee.dl.domain.model.PhotoSource
import com.uri.lee.dl.domain.model.SpeciesPhoto
import org.jetbrains.compose.resources.stringResource

/**
 * Who took a photo and under which licence. GBIF photos are mostly CC BY-NC, which requires this
 * credit wherever the photo is shown, with a link back to the source record.
 */
@Composable
internal fun PhotoCredit(photo: SpeciesPhoto, style: TextStyle, color: Color, modifier: Modifier = Modifier, showSourceLink: Boolean = true) {
    val credit = photo.credit
    Column(modifier) {
        if (photo.source == PhotoSource.USER || credit == null) {
            Text(stringResource(Res.string.photo_by_user), style = style, color = color)
            return@Column
        }
        Text(stringResource(Res.string.photo_credit, credit.creator, credit.license), style = style, color = color)
        credit.publisher?.let { Text(stringResource(Res.string.photo_credit_publisher, it), style = style, color = color) }
        val source = credit.sourceUrl
        if (showSourceLink && source != null) {
            val uriHandler = LocalUriHandler.current
            TextButton(onClick = { uriHandler.openUri(source) }) { Text(stringResource(Res.string.photo_source)) }
        }
    }
}
