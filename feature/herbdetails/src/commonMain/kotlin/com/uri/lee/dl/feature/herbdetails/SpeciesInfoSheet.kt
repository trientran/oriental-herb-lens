package com.uri.lee.dl.feature.herbdetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.uri.lee.dl.core.designsystem.component.DialogLayer
import com.uri.lee.dl.core.designsystem.component.ScientificName
import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.resources.details_basionym
import com.uri.lee.dl.core.designsystem.resources.details_class
import com.uri.lee.dl.core.designsystem.resources.details_classification
import com.uri.lee.dl.core.designsystem.resources.details_english_names
import com.uri.lee.dl.core.designsystem.resources.details_family
import com.uri.lee.dl.core.designsystem.resources.details_full
import com.uri.lee.dl.core.designsystem.resources.details_gbif_key
import com.uri.lee.dl.core.designsystem.resources.details_genus
import com.uri.lee.dl.core.designsystem.resources.details_kingdom
import com.uri.lee.dl.core.designsystem.resources.details_name
import com.uri.lee.dl.core.designsystem.resources.details_names
import com.uri.lee.dl.core.designsystem.resources.details_order
import com.uri.lee.dl.core.designsystem.resources.details_phylum
import com.uri.lee.dl.core.designsystem.resources.details_published_in
import com.uri.lee.dl.core.designsystem.resources.details_rank
import com.uri.lee.dl.core.designsystem.resources.details_scientific_name
import com.uri.lee.dl.core.designsystem.resources.details_source
import com.uri.lee.dl.core.designsystem.resources.details_status
import com.uri.lee.dl.core.designsystem.resources.details_vietnamese_names
import com.uri.lee.dl.core.designsystem.resources.details_view_on_gbif
import com.uri.lee.dl.core.designsystem.theme.HerbLensTheme
import com.uri.lee.dl.domain.model.Species
import org.jetbrains.compose.resources.stringResource

/** Everything the catalog knows about a species, so GBIF is only needed for more than that. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpeciesInfoSheet(species: Species, onOpenGbif: () -> Unit, onDismiss: () -> Unit) {
    DialogLayer {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            // Its own selection, inside the dialog layer (the app's doesn't reach into dialogs), so the
            // names and references can be copied
            SelectionContainer { SpeciesInfoContent(species, onOpenGbif) }
        }
    }
}

@Composable
fun SpeciesInfoContent(species: Species, onOpenGbif: () -> Unit) {
    val spacing = HerbLensTheme.spacing
    val taxonomy = species.taxonomy
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = spacing.xl).padding(bottom = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(stringResource(Res.string.details_full), style = MaterialTheme.typography.titleLarge)

        Section(stringResource(Res.string.details_name)) {
            Column {
                Label(stringResource(Res.string.details_scientific_name))
                ScientificName(species.scientificName, authorship = species.authorship, style = MaterialTheme.typography.bodyLarge)
            }
            if (taxonomy.basionym.isNotBlank()) {
                Column {
                    Label(stringResource(Res.string.details_basionym))
                    ScientificName(taxonomy.basionym, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Value(stringResource(Res.string.details_rank), gbifTerm(taxonomy.rank))
            Value(stringResource(Res.string.details_status), gbifTerm(taxonomy.status))
            Value(stringResource(Res.string.details_published_in), taxonomy.publishedIn)
        }

        Section(stringResource(Res.string.details_classification)) {
            Value(stringResource(Res.string.details_kingdom), taxonomy.kingdom)
            Value(stringResource(Res.string.details_phylum), taxonomy.phylum)
            Value(stringResource(Res.string.details_class), taxonomy.className)
            Value(stringResource(Res.string.details_order), taxonomy.order)
            Value(stringResource(Res.string.details_family), species.family)
            if (species.genus.isNotBlank()) {
                Column {
                    Label(stringResource(Res.string.details_genus))
                    ScientificName(species.genus, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        if (species.vietnameseNames.isNotEmpty() || species.englishNames.isNotEmpty()) {
            Section(stringResource(Res.string.details_names)) {
                Value(stringResource(Res.string.details_vietnamese_names), species.vietnameseNames.joinToString(" · "))
                Value(stringResource(Res.string.details_english_names), species.englishNames.joinToString(" · "))
            }
        }

        HorizontalDivider()
        Value(stringResource(Res.string.details_gbif_key), species.id.toString())
        Text(
            stringResource(Res.string.details_source),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onOpenGbif) {
            Text(stringResource(Res.string.details_view_on_gbif))
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, Modifier.padding(start = spacing.xs))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(HerbLensTheme.spacing.sm)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Nothing when [value] is blank. */
@Composable
private fun Value(label: String, value: String) {
    if (value.isBlank()) return
    Column {
        Label(label)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** GBIF's vocabulary as a reader would write it: "SPECIES" → "Species", "HOMOTYPIC_SYNONYM" → "Homotypic synonym". */
internal fun gbifTerm(value: String): String =
    value.trim().lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
