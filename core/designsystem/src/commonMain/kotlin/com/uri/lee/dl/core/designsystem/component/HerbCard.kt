package com.uri.lee.dl.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uri.lee.dl.core.designsystem.theme.HerbLensTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background

/**
 * One species in a list: its Vietnamese name (or the scientific name when it has none), the
 * scientific name in italics, and a supporting line such as the family or English name.
 */
@Composable
fun HerbCard(
    title: AnnotatedString,
    scientificName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    imageUrl: String? = null,
    /** Marks the species currently shown in the detail pane. */
    selected: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val spacing = HerbLensTheme.spacing
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 72.dp).padding(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            HerbAvatar(name = title.text, imageUrl = imageUrl)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (title.text != scientificName) {
                    ScientificName(
                        scientificName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (!supporting.isNullOrBlank()) {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

/** A photo when there is one; otherwise the name's first letter on a tinted circle. */
@Composable
fun HerbAvatar(name: String, imageUrl: String?, modifier: Modifier = Modifier) {
    val shape = CircleShape
    if (imageUrl != null) {
        RemoteImage(imageUrl, contentDescription = null, modifier = modifier.size(48.dp).clip(shape))
    } else {
        Box(
            modifier = modifier.size(48.dp).clip(shape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.firstOrNull()?.uppercase().orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
