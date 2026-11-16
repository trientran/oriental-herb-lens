package com.uri.lee.dl.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.uri.lee.dl.core.designsystem.component.SectionCard
import com.uri.lee.dl.core.designsystem.resources.*
import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.theme.HerbLensTheme
import com.uri.lee.dl.domain.model.Badge
import com.uri.lee.dl.domain.model.Badges
import com.uri.lee.dl.domain.model.ContributionSummary
import com.uri.lee.dl.domain.sharing.SharingProblem
import com.uri.lee.dl.domain.sharing.SharingRules
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What the signed-in user shared, their badges (only they see these) and the name they're
 * credited by. Shown back to them alone: nothing here is ranked against other people.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ContributionsCard(state: ProfileState, onAction: (ProfileAction) -> Unit) {
    val totals = state.contributions ?: return
    val spacing = HerbLensTheme.spacing
    // Marked new for this visit, while the badges are recorded as seen
    var highlighted by remember { mutableStateOf(emptySet<Badge>()) }
    LaunchedEffect(state.newBadges) {
        if (state.newBadges.isNotEmpty()) {
            highlighted = highlighted + state.newBadges
            onAction(ProfileAction.BadgesSeen)
        }
    }
    SectionCard(stringResource(Res.string.profile_contributions)) {
        if (totals.isEmpty) {
            Text(stringResource(Res.string.profile_contributions_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
            Totals(totals)
        }
        Text(stringResource(Res.string.profile_badges), style = MaterialTheme.typography.titleSmall)
        val notYet = stringResource(Res.string.profile_badge_not_yet)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Badge.entries.forEach { badge ->
                val earned = badge in state.badges
                val label = stringResource(badge.label)
                val shown = if (badge in highlighted) "$label · ${stringResource(Res.string.profile_badge_new)}" else label
                AssistChip(
                    onClick = {},
                    label = { Text(shown) },
                    leadingIcon = { Icon(if (earned) Icons.Filled.EmojiEvents else Icons.Outlined.EmojiEvents, contentDescription = null) },
                    enabled = earned,
                    colors = AssistChipDefaults.assistChipColors(leadingIconContentColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.semantics { if (!earned) contentDescription = "$label, $notYet" },
                )
            }
        }
        Text(
            stringResource(Res.string.profile_badges_note, Badges.REVIEW_DAYS),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CreditName(state.creditName, state.creditProblem) { onAction(ProfileAction.SaveCreditName(it)) }
    }
}

@Composable
private fun Totals(totals: ContributionSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(HerbLensTheme.spacing.xs)) {
        TotalRow(Res.string.profile_contributions_photos, totals.photos)
        TotalRow(Res.string.profile_contributions_species, totals.species)
        TotalRow(Res.string.profile_contributions_names, totals.names)
        TotalRow(Res.string.profile_contributions_models, totals.models)
        if (totals.models > 0) TotalRow(Res.string.profile_contributions_adds, totals.modelAdds)
    }
}

@Composable
private fun TotalRow(label: StringResource, value: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value.toString(), style = MaterialTheme.typography.titleMedium)
    }
}

/** The name to be credited by on what the user shares from now on; empty means "a Med Herb Lens user". */
@Composable
private fun CreditName(saved: String?, problem: SharingProblem?, onSave: (String) -> Unit) {
    val spacing = HerbLensTheme.spacing
    var text by remember(saved) { mutableStateOf(saved.orEmpty()) }
    Text(stringResource(Res.string.profile_credit), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(Res.string.profile_credit_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(SharingRules.MAX_CREDIT_NAME) },
            placeholder = { Text(stringResource(Res.string.profile_credit_hint)) },
            singleLine = true,
            isError = problem != null,
            supportingText = problem?.let { { Text(stringResource(it.creditText)) } },
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { onSave(text) }, enabled = text.trim() != saved.orEmpty()) { Text(stringResource(Res.string.profile_credit_save)) }
    }
}

private val SharingProblem.creditText: StringResource
    get() = when (this) {
        SharingProblem.CONTACT_DETAILS -> Res.string.profile_credit_problem_contact
        SharingProblem.OFFENSIVE_WORDS -> Res.string.profile_credit_problem_words
        else -> Res.string.profile_credit_problem_length
    }

private val Badge.label: StringResource
    get() = when (this) {
        Badge.FIRST_PHOTO -> Res.string.badge_first_photo
        Badge.PHOTOS_10 -> Res.string.badge_photos_10
        Badge.PHOTOS_50 -> Res.string.badge_photos_50
        Badge.SPECIES_5 -> Res.string.badge_species_5
        Badge.SPECIES_25 -> Res.string.badge_species_25
        Badge.GAP_FILLER -> Res.string.badge_gap_filler
        Badge.FIRST_NAME -> Res.string.badge_first_name
        Badge.NAMES_10 -> Res.string.badge_names_10
        Badge.FIRST_MODEL -> Res.string.badge_first_model
        Badge.MODEL_ADDS_10 -> Res.string.badge_model_adds_10
    }
