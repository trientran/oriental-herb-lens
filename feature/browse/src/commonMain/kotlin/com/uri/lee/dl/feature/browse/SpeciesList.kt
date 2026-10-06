package com.uri.lee.dl.feature.browse

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import com.uri.lee.dl.core.designsystem.LocalVietnameseFirst
import com.uri.lee.dl.core.designsystem.component.HerbCard
import com.uri.lee.dl.domain.model.Species

/** A catalog entry as a [HerbCard]; [selected] marks the species shown in the detail pane. */
@Composable
internal fun SpeciesCard(
    species: Species,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: AnnotatedString = AnnotatedString(species.displayName(LocalVietnameseFirst.current)),
    supporting: String? = species.otherName(LocalVietnameseFirst.current) ?: species.family,
) {
    HerbCard(
        title = title,
        scientificName = species.scientificName,
        supporting = supporting,
        onClick = onClick,
        selected = selected,
        modifier = modifier,
    )
}
