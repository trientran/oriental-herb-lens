package com.uri.lee.dl.core.designsystem.component

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle

/** A scientific name: always italic, by botanical convention; the authorship (if any) is not. */
@Composable
fun ScientificName(
    name: String,
    modifier: Modifier = Modifier,
    authorship: String? = null,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(name) }
            if (!authorship.isNullOrBlank()) append(" $authorship")
        },
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** [text] with [range] (a search match) in bold and the primary colour. */
@Composable
fun highlighted(text: String, range: IntRange?): AnnotatedString {
    if (range == null || range.isEmpty() || range.last >= text.length) return AnnotatedString(text)
    val accent = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        append(text.substring(0, range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accent)) { append(text.substring(range)) }
        append(text.substring(range.last + 1))
    }
}
