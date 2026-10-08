package com.uri.lee.dl.feature.training

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uri.lee.dl.core.designsystem.component.AiTerm
import com.uri.lee.dl.core.designsystem.component.DialogLayer
import com.uri.lee.dl.core.designsystem.component.EmptyState
import com.uri.lee.dl.core.designsystem.component.InfoTip
import com.uri.lee.dl.core.designsystem.component.LoadingState
import com.uri.lee.dl.core.designsystem.component.RemoteImage
import com.uri.lee.dl.core.designsystem.component.TermChip
import com.uri.lee.dl.core.designsystem.resources.*
import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.theme.HerbLensTheme
import com.uri.lee.dl.domain.media.PhotoPick
import com.uri.lee.dl.domain.media.PickPhotos
import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.sharing.HuggingFaceStatus
import com.uri.lee.dl.domain.sharing.ModelReportReason
import com.uri.lee.dl.domain.sharing.SharingProblem
import com.uri.lee.dl.domain.training.Dataset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** A file the user picked: its name and contents. */
class PickedFile(val name: String, val bytes: ByteArray)

/** What each platform provides to the training screens; a null entry hides its button. */
class TrainingPlatform(
    val pickPhotos: PickPhotos,
    val pickDatasetFolder: (suspend () -> Dataset?)? = null,
    val pickDatasetZip: (suspend () -> Dataset?)? = null,
    val pickModelFile: (suspend () -> PickedFile?)? = null,
    /** Hands a file to the user: share sheet, save dialog or download. */
    val saveFile: suspend (name: String, bytes: ByteArray) -> Unit,
    /** The live camera; each frame goes to onFrame with its aspect ratio. */
    val camera: (@Composable (onFrame: suspend (ClassifierImage, Float) -> Unit, modifier: Modifier) -> Unit)? = null,
    /** Opens sign-in, which sharing with everyone and reporting need. */
    val signIn: () -> Unit = {},
)

/** Fewer than this many photos of a species and the screen asks for more. */
private const val MIN_PHOTOS = 10

@Composable
fun TrainingRoute(platform: TrainingPlatform, modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<TrainingViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    TrainingScreen(state, viewModel::onAction, platform, viewModel, modifier)
}

@Composable
internal fun TrainingScreen(
    state: TrainingState,
    onAction: (TrainingAction) -> Unit,
    platform: TrainingPlatform,
    viewModel: TrainingViewModel?,
    modifier: Modifier = Modifier,
) {
    val spacing = HerbLensTheme.spacing
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = spacing.maxContentWidth).fillMaxWidth()) {
            when (val screen = state.screen) {
                TrainingScreen.Models -> ModelsScreen(state, onAction, platform)
                TrainingScreen.NewModel -> NewModelScreen(onAction, platform)
                is TrainingScreen.Edit -> EditScreen(state, onAction, platform)
                is TrainingScreen.Training -> TrainingProgressScreen(state, onAction)
                is TrainingScreen.Result -> ResultScreen(state, onAction, platform, viewModel)
                is TrainingScreen.Settings -> SettingsScreen(state, onAction)
                is TrainingScreen.Use -> UseScreen(state, onAction, platform, viewModel)
                TrainingScreen.Community -> CommunityScreen(state, onAction)
                is TrainingScreen.CommunityModel -> CommunityModelScreen(state, screen.id, onAction)
            }
        }
    }
    state.error?.let { error ->
        DialogLayer {
            AlertDialog(
                onDismissRequest = { onAction(TrainingAction.DismissError) },
                confirmButton = { TextButton(onClick = { onAction(TrainingAction.DismissError) }) { Text(stringResource(Res.string.train_ok)) } },
                text = { Text(stringResource(error.message)) },
            )
        }
    }
    state.sharing?.let { SharingDialog(it, onAction, platform) }
    state.message?.let { message ->
        DialogLayer {
            AlertDialog(
                onDismissRequest = { onAction(TrainingAction.DismissMessage) },
                confirmButton = { TextButton(onClick = { onAction(TrainingAction.DismissMessage) }) { Text(stringResource(Res.string.train_ok)) } },
                text = { Text(stringResource(message.text)) },
            )
        }
    }
}

private val TrainingMessage.text: StringResource
    get() = when (this) {
        TrainingMessage.SHARED -> Res.string.train_msg_shared
        TrainingMessage.SHARED_HUGGING_FACE -> Res.string.train_msg_shared_hf
        TrainingMessage.SHARED_HUGGING_FACE_LATER -> Res.string.train_msg_shared_hf_later
        TrainingMessage.SHARE_LATER -> Res.string.train_msg_share_later
        TrainingMessage.ADDED -> Res.string.train_msg_added
        TrainingMessage.REPORTED -> Res.string.train_msg_reported
        TrainingMessage.HIDDEN -> Res.string.train_msg_hidden
        TrainingMessage.REMOVED -> Res.string.train_msg_removed
    }

/** What sharing with everyone asks of the user first: to sign in, to accept the terms, or to fix a name. */
@Composable
private fun SharingDialog(step: SharingStep, onAction: (TrainingAction) -> Unit, platform: TrainingPlatform) {
    val dismiss = { onAction(TrainingAction.DismissSharing) }
    DialogLayer {
        when (step) {
            SharingStep.SignIn -> AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(stringResource(Res.string.train_share_everyone)) },
                text = { Text(stringResource(Res.string.train_share_sign_in)) },
                confirmButton = { TextButton(onClick = { dismiss(); platform.signIn() }) { Text(stringResource(Res.string.profile_sign_in)) } },
                dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(Res.string.cancel)) } },
            )
            SharingStep.Terms -> AlertDialog(
                onDismissRequest = dismiss,
                icon = { Icon(Icons.Filled.Public, contentDescription = null) },
                title = { Text(stringResource(Res.string.train_share_terms_title)) },
                text = {
                    Text(
                        stringResource(Res.string.train_share_terms_body),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                },
                confirmButton = { TextButton(onClick = { onAction(TrainingAction.AcceptSharingTerms) }) { Text(stringResource(Res.string.train_share_accept)) } },
                dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(Res.string.cancel)) } },
            )
            SharingStep.Confirm -> ConfirmShareDialog(onDismiss = dismiss) { onAction(TrainingAction.ConfirmShare(it)) }
            is SharingStep.Problem -> AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(stringResource(Res.string.train_share_everyone)) },
                text = { Text(stringResource(step.problem.text)) },
                confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(Res.string.train_ok)) } },
            )
        }
    }
}

/** The last step before sharing, with publishing on Hugging Face too (ticked, can be unticked). */
@Composable
private fun ConfirmShareDialog(onDismiss: () -> Unit, onShare: (offerToHuggingFace: Boolean) -> Unit) {
    // On by default (the model is public under CC BY 4.0 either way); the sharer can untick it
    var huggingFace by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Public, contentDescription = null) },
        title = { Text(stringResource(Res.string.train_share_confirm_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(HerbLensTheme.spacing.md)) {
                Text(stringResource(Res.string.train_share_confirm_body), style = MaterialTheme.typography.bodyMedium)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .toggleable(value = huggingFace, role = Role.Checkbox, onValueChange = { huggingFace = it }),
                    verticalAlignment = Alignment.Top,
                ) {
                    Checkbox(checked = huggingFace, onCheckedChange = null)
                    Column(Modifier.padding(start = HerbLensTheme.spacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(Res.string.train_share_hf), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                            InfoTip(AiTerm.HUGGING_FACE)
                        }
                        Text(
                            stringResource(Res.string.train_share_hf_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onShare(huggingFace) }) { Text(stringResource(Res.string.train_share_button)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

private val SharingProblem.text: StringResource
    get() = when (this) {
        SharingProblem.NAME_LENGTH -> Res.string.train_share_problem_name
        SharingProblem.TOO_FEW_SPECIES -> Res.string.train_share_problem_species
        SharingProblem.CONTACT_DETAILS -> Res.string.train_share_problem_contact
        SharingProblem.OFFENSIVE_WORDS -> Res.string.train_share_problem_words
        SharingProblem.TOO_LARGE -> Res.string.train_share_problem_size
    }

private val TrainingError.message: StringResource
    get() = when (this) {
        TrainingError.READ_PHOTOS -> Res.string.train_error_read
        TrainingError.DOWNLOAD -> Res.string.train_error_download
        TrainingError.TRAIN -> Res.string.train_error_train
        TrainingError.NO_LABELS -> Res.string.train_error_no_labels
        TrainingError.IMPORT -> Res.string.train_error_import
        TrainingError.TOO_FEW_SPECIES -> Res.string.train_error_species
        TrainingError.OPEN_MODEL -> Res.string.train_error_open
        TrainingError.SHARE -> Res.string.train_error_share
        TrainingError.COMMUNITY -> Res.string.train_error_community
    }

@Composable
private fun Page(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    val spacing = HerbLensTheme.spacing
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back)) }
            }
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(horizontal = spacing.sm))
            actions()
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = spacing.lg, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
            content = content,
        )
    }
}

@Composable
private fun Work(work: TrainingWork?) {
    when (work) {
        null -> Unit
        TrainingWork.Downloading -> Progress(stringResource(Res.string.train_downloading), null)
        is TrainingWork.Reading -> Progress(stringResource(Res.string.train_reading, work.done, work.total), work.done.toFloat() / work.total.coerceAtLeast(1))
        is TrainingWork.Learning -> Progress(stringResource(Res.string.train_epoch, work.epoch), null)
        TrainingWork.Saving -> Progress(stringResource(Res.string.train_saving), null)
        TrainingWork.Uploading -> Progress(stringResource(Res.string.train_sharing), null)
    }
}

@Composable
private fun Progress(text: String, fraction: Float?) {
    Column(verticalArrangement = Arrangement.spacedBy(HerbLensTheme.spacing.xs)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (fraction == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ChoiceRow(icon: ImageVector, title: String, supporting: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
    )
}

@Composable
private fun ModelsScreen(state: TrainingState, onAction: (TrainingAction) -> Unit, platform: TrainingPlatform) {
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf<UserModel?>(null) }
    var deleting by remember { mutableStateOf<UserModel?>(null) }
    renaming?.let { model ->
        NameDialog(Res.string.train_rename, model.name, onDismiss = { renaming = null }) {
            onAction(TrainingAction.Rename(model.id, it))
            renaming = null
        }
    }
    deleting?.let { model ->
        ConfirmDeleteDialog(model.name, onDismiss = { deleting = null }) {
            onAction(TrainingAction.DeleteModel(model.id))
            deleting = null
        }
    }
    Page(stringResource(Res.string.train_models_title), onBack = null) {
        TrainIntro()
        val models = state.models
        when {
            models == null -> LoadingState()
            models.isEmpty() -> EmptyState(
                icon = Icons.Filled.Psychology,
                title = stringResource(Res.string.train_models_empty_title),
                body = stringResource(Res.string.train_models_empty_body),
            )
            else -> models.forEach { model ->
                val status = when {
                    model.imported -> stringResource(Res.string.train_model_imported)
                    model.importedTrainable && model.report == null -> stringResource(Res.string.train_model_imported_trainable)
                    model.report != null -> "${percent(model.report.accuracy)} · " + stringResource(Res.string.train_model_trained_here)
                    else -> stringResource(Res.string.train_model_untrained)
                }
                ModelRow(
                    model = model,
                    supporting = stringResource(Res.string.train_model_summary, model.classes.size, status),
                    onOpen = { onAction(TrainingAction.Open(if (model.imported) TrainingScreen.Use(model.id) else TrainingScreen.Edit(model.id))) },
                    // Only models trained here, not yet shared
                    onShare = if (model.report != null && model.sharedId == null) {
                        { onAction(TrainingAction.ShareModel(model.id)) }
                    } else {
                        null
                    },
                    onRename = { renaming = model },
                    onDelete = { deleting = model },
                )
            }
        }
        Work(state.work)
        Button(onClick = { onAction(TrainingAction.Open(TrainingScreen.NewModel)) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.train_new_model))
        }
        platform.pickModelFile?.let { pick ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { scope.launch { pick()?.let { onAction(TrainingAction.ImportModel(it.name, it.bytes)) } } },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.FileOpen, contentDescription = null)
                    Text(stringResource(Res.string.train_import_model), Modifier.padding(start = HerbLensTheme.spacing.sm))
                }
                InfoTip(AiTerm.TFLITE)
            }
        }
        OutlinedButton(onClick = { onAction(TrainingAction.Open(TrainingScreen.Community)) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Public, contentDescription = null)
            Text(stringResource(Res.string.train_community), Modifier.padding(start = HerbLensTheme.spacing.sm))
        }
    }
}

/**
 * What the Train tab is for, at its top: a new user can't tell otherwise what it trains, or that
 * it's image classification only. The terms it uses explain themselves.
 */
@Composable
private fun TrainIntro() {
    val spacing = HerbLensTheme.spacing
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(stringResource(Res.string.train_intro_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(Res.string.train_intro_body), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(Res.string.train_intro_steps), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            TermChip(AiTerm.AI_MODEL)
            TermChip(AiTerm.IMAGE_CLASSIFICATION)
            TermChip(AiTerm.TRAINING)
        }
    }
}

/** A setting's or section's heading, with a tip on the term it names. */
@Composable
private fun TermHeading(text: String, term: AiTerm) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall)
        InfoTip(term)
    }
}

/** A caption under a chart or result, with a tip on the term it relies on. */
@Composable
private fun TermCaption(text: String, term: AiTerm, style: TextStyle = MaterialTheme.typography.bodySmall) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        InfoTip(term)
    }
}

/** Asks, on a trained model's results, to share it: others can use it, and it helps the research. */
@Composable
private fun ShareInvite(enabled: Boolean, onShare: () -> Unit) {
    val spacing = HerbLensTheme.spacing
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            stringResource(Res.string.train_share_invite_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            stringResource(Res.string.train_share_invite_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Button(onClick = onShare, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Public, contentDescription = null)
            Text(stringResource(Res.string.train_share_everyone), Modifier.padding(start = spacing.sm))
        }
    }
}

/** Models others shared; each opens [CommunityModelScreen]. */
@Composable
private fun CommunityScreen(state: TrainingState, onAction: (TrainingAction) -> Unit) {
    Page(stringResource(Res.string.train_community_title), onBack = { onAction(TrainingAction.Back) }) {
        Text(
            stringResource(Res.string.train_community_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val models = state.community
        when {
            models == null -> LoadingState()
            models.isEmpty() -> EmptyState(
                icon = Icons.Filled.Public,
                title = stringResource(Res.string.train_community_empty_title),
                body = stringResource(Res.string.train_community_empty_body),
            )
            else -> models.forEach { model ->
                val preview = model.species.take(3).joinToString(", ") + if (model.species.size > 3) "…" else ""
                ChoiceRow(
                    Icons.Filled.Psychology,
                    model.name,
                    supporting = pluralStringResource(Res.plurals.train_species_count, model.species.size, model.species.size) + ": " + preview +
                        " · " + stringResource(Res.string.train_size_mb, megabytes(model.sizeBytes)) +
                        if (model.huggingFace == HuggingFaceStatus.PUBLISHED) " · Hugging Face" else "",
                ) { onAction(TrainingAction.Open(TrainingScreen.CommunityModel(model.id))) }
            }
        }
    }
}

/** One shared model: add it, see what it can identify; report it or hide its sharer, or stop sharing your own. */
@Composable
private fun CommunityModelScreen(state: TrainingState, id: String, onAction: (TrainingAction) -> Unit) {
    val model = state.community?.firstOrNull { it.id == id } ?: return LoadingState()
    val spacing = HerbLensTheme.spacing
    val uriHandler = LocalUriHandler.current
    var reporting by remember { mutableStateOf(false) }
    var hiding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    Page(model.name, onBack = { onAction(TrainingAction.Back) }) {
        Text(
            listOfNotNull(
                pluralStringResource(Res.plurals.train_species_count, model.species.size, model.species.size),
                stringResource(Res.string.train_size_mb, megabytes(model.sizeBytes)),
                if (model.trainable) stringResource(Res.string.train_can_learn) else null,
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Work(state.work)
        Button(onClick = { onAction(TrainingAction.AddCommunityModel(model.id)) }, enabled = state.work == null, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Download, contentDescription = null)
            Text(stringResource(Res.string.train_community_add), Modifier.padding(start = spacing.sm))
        }
        val own = model.uploaderId == state.userId
        val huggingFaceUrl = model.huggingFaceUrl
        when {
            model.huggingFace == HuggingFaceStatus.PUBLISHED && huggingFaceUrl != null ->
                OutlinedButton(onClick = { uriHandler.openUri(huggingFaceUrl) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Text(stringResource(Res.string.train_hf_open), Modifier.padding(start = spacing.sm))
                }
            own && model.huggingFace == HuggingFaceStatus.REQUESTED ->
                Text(stringResource(Res.string.train_hf_requested), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            own && model.huggingFace == HuggingFaceStatus.DECLINED ->
                Text(stringResource(Res.string.train_hf_declined), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        model.species.sortedBy { it.lowercase() }.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (own) {
            OutlinedButton(onClick = { removing = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.train_community_remove)) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                TextButton(onClick = { reporting = true }) {
                    Icon(Icons.Filled.Flag, contentDescription = null)
                    Text(stringResource(Res.string.train_community_report), Modifier.padding(start = spacing.xs))
                }
                TextButton(onClick = { hiding = true }) {
                    Icon(Icons.Filled.Block, contentDescription = null)
                    Text(stringResource(Res.string.train_community_hide), Modifier.padding(start = spacing.xs))
                }
            }
        }
    }
    if (reporting) ReportModelDialog(onDismiss = { reporting = false }) { reporting = false; onAction(TrainingAction.ReportCommunityModel(model.id, it)) }
    if (hiding) {
        ConfirmDialog(Res.string.train_community_hide_title, Res.string.train_community_hide_body, Res.string.hide, onDismiss = { hiding = false }) {
            hiding = false
            onAction(TrainingAction.HideSharer(model.uploaderId))
        }
    }
    if (removing) {
        ConfirmDialog(Res.string.train_community_remove_title, Res.string.train_community_remove_body, Res.string.train_community_remove, onDismiss = { removing = false }) {
            removing = false
            onAction(TrainingAction.RemoveCommunityModel(model.id))
        }
    }
}

@Composable
private fun ConfirmDialog(title: StringResource, body: StringResource, confirm: StringResource, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    DialogLayer {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(title)) },
            text = { Text(stringResource(body)) },
            confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirm)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

@Composable
private fun ReportModelDialog(onDismiss: () -> Unit, onReport: (ModelReportReason) -> Unit) {
    var reason by remember { mutableStateOf<ModelReportReason?>(null) }
    DialogLayer {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.train_community_report_title)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    Text(stringResource(Res.string.train_community_report_body), style = MaterialTheme.typography.bodyMedium)
                    ModelReportReason.entries.forEach { entry ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(selected = reason == entry, onClick = { reason = entry }, role = Role.RadioButton),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = reason == entry, onClick = null)
                            Text(stringResource(entry.label), Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { reason?.let(onReport) }, enabled = reason != null) { Text(stringResource(Res.string.train_community_report)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

private val ModelReportReason.label: StringResource
    get() = when (this) {
        ModelReportReason.OFFENSIVE -> Res.string.train_report_offensive
        ModelReportReason.PERSONAL_INFORMATION -> Res.string.train_report_personal
        ModelReportReason.MISLEADING -> Res.string.train_report_misleading
        ModelReportReason.SPAM -> Res.string.train_report_spam
        ModelReportReason.OTHER -> Res.string.train_report_other
    }

/** "12.3": one decimal, without platform number formatting (not on every target). */
private fun megabytes(bytes: Int): String {
    val tenths = (bytes / 104_857.6).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}

/** A model in the list: opens it, and a menu to rename or delete it. */
@Composable
private fun ModelRow(model: UserModel, supporting: String, onOpen: () -> Unit, onShare: (() -> Unit)?, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(model.name) },
        supportingContent = { Text(supporting) },
        leadingContent = { Icon(if (model.imported || model.importedTrainable) Icons.Filled.Download else Icons.Filled.Eco, contentDescription = null) },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.train_more_options)) }
                // A popup: kept out of the web page's text selection, like dialogs
                DialogLayer {
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        onShare?.let { share ->
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.train_share_everyone)) },
                                leadingIcon = { Icon(Icons.Filled.Public, contentDescription = null) },
                                onClick = { menu = false; share() },
                            )
                        }
                        DropdownMenuItem(text = { Text(stringResource(Res.string.train_rename)) }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.train_delete), color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen),
    )
}

@Composable
private fun NameDialog(title: StringResource, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    DialogLayer {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(title)) },
            text = { OutlinedTextField(name, { name = it }, label = { Text(stringResource(Res.string.train_name)) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text(stringResource(Res.string.train_save)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.train_cancel)) } },
        )
    }
}

@Composable
private fun ConfirmDeleteDialog(name: String, onDismiss: () -> Unit, onDelete: () -> Unit) {
    DialogLayer {
        AlertDialog(
            onDismissRequest = onDismiss,
            text = { Text(stringResource(Res.string.train_delete_confirm, name)) },
            confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(Res.string.train_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.train_cancel)) } },
        )
    }
}

@Composable
private fun QualityChips(selected: Quality, enabled: Boolean = true, onSelect: (Quality) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(HerbLensTheme.spacing.sm)) {
        Quality.entries.forEach { quality ->
            FilterChip(
                selected = quality == selected,
                onClick = { onSelect(quality) },
                enabled = enabled,
                label = {
                    Text(
                        stringResource(
                            when (quality) {
                                Quality.FAST -> Res.string.train_quality_fast
                                Quality.BALANCED -> Res.string.train_quality_balanced
                                Quality.BEST -> Res.string.train_quality_best
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun NewModelScreen(onAction: (TrainingAction) -> Unit, platform: TrainingPlatform) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf(Quality.BALANCED) }
    val placeholder = stringResource(Res.string.train_name_placeholder)
    val chosenName = { name.ifBlank { placeholder } }
    Page(stringResource(Res.string.train_new_title), onBack = { onAction(TrainingAction.Back) }) {
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(Res.string.train_name)) }, placeholder = { Text(placeholder) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        TermHeading(stringResource(Res.string.train_quality), AiTerm.BASE_MODEL)
        QualityChips(quality) { quality = it }
        Text(stringResource(Res.string.train_how_photos), style = MaterialTheme.typography.titleSmall)
        ChoiceRow(Icons.Filled.PhotoCamera, stringResource(Res.string.train_collect), stringResource(Res.string.train_collect_body)) {
            onAction(TrainingAction.CreateModel(chosenName(), quality))
        }
        // A zip first where there is one: some phones' folder pickers show no folders
        platform.pickDatasetZip?.let { pick ->
            ChoiceRow(Icons.Filled.FolderZip, stringResource(Res.string.train_import_zip), stringResource(Res.string.train_import_body)) {
                scope.launch { pick()?.takeIf { it.classes.size >= 2 }?.let { onAction(TrainingAction.ImportDataset(chosenName(), quality, it)) } }
            }
        }
        platform.pickDatasetFolder?.let { pick ->
            val body = stringResource(Res.string.train_import_body) +
                if (platform.pickDatasetZip != null) "\n" + stringResource(Res.string.train_folder_empty_hint) else ""
            ChoiceRow(Icons.Filled.Folder, stringResource(Res.string.train_import_folder), body) {
                scope.launch { pick()?.takeIf { it.classes.size >= 2 }?.let { onAction(TrainingAction.ImportDataset(chosenName(), quality, it)) } }
            }
        }
    }
}

@Composable
private fun EditScreen(state: TrainingState, onAction: (TrainingAction) -> Unit, platform: TrainingPlatform) {
    val model = state.model ?: return LoadingState()
    val spacing = HerbLensTheme.spacing
    var adding by remember { mutableStateOf(false) }
    Page(
        model.name,
        onBack = { onAction(TrainingAction.Back) },
        actions = {
            IconButton(onClick = { onAction(TrainingAction.Open(TrainingScreen.Settings(model.id))) }) {
                Icon(Icons.Filled.Tune, contentDescription = stringResource(Res.string.train_settings))
            }
        },
    ) {
        model.classes.forEach { species ->
            val count = model.photoCounts[species] ?: 0
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).padding(vertical = spacing.xs),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(species, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (count < MIN_PHOTOS) stringResource(Res.string.train_add_at_least, count, MIN_PHOTOS) else stringResource(Res.string.train_photos_count, count),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (count < MIN_PHOTOS) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { platform.pickPhotos(PhotoPick { photos -> if (photos.isNotEmpty()) onAction(TrainingAction.AddPhotos(species, photos)) }) },
                        enabled = state.work == null,
                    ) { Icon(Icons.Filled.AddAPhoto, contentDescription = stringResource(Res.string.train_add_photos)) }
                }
                state.thumbnails[species]?.takeIf { it.isNotEmpty() }?.let { uris ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        uris.takeLast(12).forEach { uri ->
                            RemoteImage(uri, contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)))
                        }
                    }
                }
                HorizontalDivider()
            }
        }
        OutlinedButton(onClick = { adding = true }, enabled = state.work == null, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.train_add_species))
        }
        Work(state.work)
        val trained = model.isTrained
        Button(onClick = { onAction(TrainingAction.Train) }, enabled = state.work == null && model.photos > 0, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (trained) Res.string.train_retrain else Res.string.train_train))
        }
        if (trained && model.report != null) {
            OutlinedButton(onClick = { onAction(TrainingAction.Open(TrainingScreen.Result(model.id))) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.train_see_results))
            }
        }
        if (trained) {
            OutlinedButton(onClick = { onAction(TrainingAction.Open(TrainingScreen.Use(model.id))) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.train_try_it))
            }
        }
        // Trained here (it has results) and not shared yet: the same invitation as on the results
        if (model.report != null && model.sharedId == null) {
            ShareInvite(enabled = state.work == null) { onAction(TrainingAction.ShareWithEveryone) }
        }
    }
    if (adding) AddSpeciesDialog(onDismiss = { adding = false }) { onAction(TrainingAction.AddSpecies(it)); adding = false }
}

@Composable
private fun AddSpeciesDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    DialogLayer {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.train_add_species)) },
            text = { OutlinedTextField(name, { name = it }, label = { Text(stringResource(Res.string.train_species_name)) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onAdd(name) }, enabled = name.isNotBlank()) { Text(stringResource(Res.string.train_add)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.train_cancel)) } },
        )
    }
}

@Composable
private fun TrainingProgressScreen(state: TrainingState, onAction: (TrainingAction) -> Unit) {
    Page(stringResource(Res.string.train_training_title), onBack = null) {
        val last = state.history.lastOrNull()
        if (last != null && state.work is TrainingWork.Learning) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.train_progress_accuracy, last.epoch, percent(last.validationAccuracy)),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                InfoTip(AiTerm.ROUND)
            }
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            Work(state.work)
        }
        AccuracyChart(state.history)
        // Planted while they wait: the results screen then offers to share
        if (state.model?.sharedId == null) {
            Text(
                stringResource(Res.string.train_training_share_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = { onAction(TrainingAction.StopTraining) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.train_stop)) }
    }
}

private val TRAIN_COLOR = Color(0xFF378ADD)
private val CHECK_COLOR = Color(0xFFD85A30)

/** Share of checking photos right after each round: the curve anyone can read. */
@Composable
private fun AccuracyChart(history: List<com.uri.lee.dl.core.training.EpochStats>) {
    LineChart(
        lines = listOf(ChartLine(CHECK_COLOR, history.map { if (it.validationAccuracy.isNaN()) 0f else it.validationAccuracy })),
        yMax = 1f,
        yLabel = stringResource(Res.string.train_chart_accuracy),
        xLabel = stringResource(Res.string.train_chart_rounds),
        format = { "${(it * 100).roundToInt()}" },
        modifier = Modifier.fillMaxWidth(),
    )
    TermCaption(stringResource(Res.string.train_accuracy_caption), AiTerm.CHECKING_PHOTOS)
}

/** Both losses by round, and the round that was kept (lowest checking loss). */
@Composable
private fun LossChart(history: List<com.uri.lee.dl.core.training.EpochStats>) {
    val top = history.maxOfOrNull { maxOf(it.trainLoss, it.validationLoss) }?.coerceAtLeast(0.01f) ?: 1f
    val kept = history.minByOrNull { it.validationLoss }?.epoch
    LineChart(
        lines = listOf(ChartLine(TRAIN_COLOR, history.map { it.trainLoss }), ChartLine(CHECK_COLOR, history.map { it.validationLoss })),
        yMax = top,
        yLabel = stringResource(Res.string.train_chart_loss),
        xLabel = stringResource(Res.string.train_chart_rounds),
        format = { v -> ((v * 100).roundToInt() / 100.0).toString() },
        marker = kept,
        modifier = Modifier.fillMaxWidth(),
    )
    TermCaption(stringResource(Res.string.train_curve_legend), AiTerm.LOSS)
}

@Composable
private fun ResultScreen(state: TrainingState, onAction: (TrainingAction) -> Unit, platform: TrainingPlatform, viewModel: TrainingViewModel?) {
    val model = state.model ?: return LoadingState()
    val report = model.report ?: return LoadingState()
    val spacing = HerbLensTheme.spacing
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    Page(stringResource(Res.string.train_ready, model.name), onBack = { onAction(TrainingAction.Back) }) {
        Text(percent(report.accuracy), style = MaterialTheme.typography.displaySmall)
        TermCaption(
            stringResource(if (report.heldOut) Res.string.train_right_on_unseen else Res.string.train_right_on_checking, report.testPhotos),
            AiTerm.TEST_ACCURACY,
            style = MaterialTheme.typography.bodyMedium,
        )
        report.perClass.forEach { (species, accuracy) ->
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Row {
                    Text(species, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(if (accuracy.isNaN()) "–" else percent(accuracy), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LinearProgressIndicator(progress = { if (accuracy.isNaN()) 0f else accuracy }, modifier = Modifier.fillMaxWidth())
            }
        }
        report.confused?.let { (a, b) ->
            Text(stringResource(Res.string.train_mixed_up, a, b), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = { onAction(TrainingAction.Open(TrainingScreen.Use(model.id))) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
            Text(stringResource(Res.string.train_try_it), Modifier.padding(start = spacing.sm))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            OutlinedButton(onClick = { onAction(TrainingAction.Open(TrainingScreen.Edit(model.id))) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.train_add_more))
            }
            OutlinedButton(
                onClick = { scope.launch { viewModel?.export()?.let { (name, bytes) -> platform.saveFile(name, bytes) } } },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(Res.string.train_share)) }
            InfoTip(AiTerm.TFLITE, Modifier.align(Alignment.CenterVertically))
        }
        Work(state.work)
        if (model.sharedId == null) {
            ShareInvite(enabled = state.work == null) { onAction(TrainingAction.ShareWithEveryone) }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Icon(Icons.Filled.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(Res.string.train_shared_already), style = MaterialTheme.typography.bodyMedium)
            }
        }
        UnderTheHood(report)
        TextButton(onClick = { confirmDelete = true }) { Text(stringResource(Res.string.train_delete), color = MaterialTheme.colorScheme.error) }
    }
    if (confirmDelete) ConfirmDeleteDialog(model.name, onDismiss = { confirmDelete = false }) { confirmDelete = false; onAction(TrainingAction.Delete) }
}

/** The learning curve and the confusion matrix, for those who want to see how it learned. */
@Composable
private fun UnderTheHood(report: ModelReport) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) { Text(stringResource(Res.string.train_under_the_hood)) }
    if (!open) return
    if (report.history.size >= 2) {
        AccuracyChart(report.history)
        LossChart(report.history)
    }
    if (report.confusion.isNotEmpty()) {
        TermCaption(stringResource(Res.string.train_confusion), AiTerm.CONFUSION)
        Column {
            report.confusion.forEach { row ->
                Row {
                    row.forEach { count ->
                        Text(
                            count.toString(),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (count == 0) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: TrainingState, onAction: (TrainingAction) -> Unit) {
    val model = state.model ?: return LoadingState()
    var settings by remember(model.id) { mutableStateOf(model.settings) }
    var advanced by remember { mutableStateOf(false) }
    val spacing = HerbLensTheme.spacing
    Page(stringResource(Res.string.train_settings), onBack = { onAction(TrainingAction.Back) }) {
        TermHeading(stringResource(Res.string.train_quality), AiTerm.BASE_MODEL)
        val fixed = model.photos > 0
        QualityChips(settings.quality, enabled = !fixed) { settings = settings.copy(quality = it) }
        if (fixed) Text(stringResource(Res.string.train_quality_fixed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TermHeading(stringResource(Res.string.train_when_adding), AiTerm.CONTINUAL)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            FilterChip(settings.update == UpdateMode.REPLAY, { settings = settings.copy(update = UpdateMode.REPLAY) }, label = { Text(stringResource(Res.string.train_update_replay)) })
            FilterChip(settings.update == UpdateMode.RETRAIN_ALL, { settings = settings.copy(update = UpdateMode.RETRAIN_ALL) }, label = { Text(stringResource(Res.string.train_update_retrain)) })
        }
        TextButton(onClick = { advanced = !advanced }) { Text(stringResource(Res.string.train_advanced)) }
        if (advanced) {
            val e = settings.expert
            fun set(expert: ExpertSettings) { settings = settings.copy(expert = expert) }
            NumberField(Res.string.train_learning_rate, AiTerm.LEARNING_RATE, e.learningRate.toString()) { it.toFloatOrNull()?.takeIf { v -> v > 0 }?.let { v -> set(e.copy(learningRate = v)) } }
            NumberField(Res.string.train_batch_size, AiTerm.BATCH_SIZE, e.batchSize.toString()) { it.toIntOrNull()?.takeIf { v -> v > 0 }?.let { v -> set(e.copy(batchSize = v)) } }
            NumberField(Res.string.train_max_epochs, AiTerm.ROUND, e.maxEpochs.toString()) { it.toIntOrNull()?.takeIf { v -> v > 0 }?.let { v -> set(e.copy(maxEpochs = v)) } }
            NumberField(Res.string.train_patience, AiTerm.EARLY_STOPPING, e.patience.toString()) { it.toIntOrNull()?.takeIf { v -> v > 0 }?.let { v -> set(e.copy(patience = v)) } }
            NumberField(Res.string.train_l2, AiTerm.WEIGHT_DECAY, e.l2.toString()) { it.toFloatOrNull()?.takeIf { v -> v >= 0 }?.let { v -> set(e.copy(l2 = v)) } }
            NumberField(Res.string.train_validation_share, AiTerm.CHECKING_PHOTOS, e.validationShare.toString()) { it.toFloatOrNull()?.takeIf { v -> v in 0f..0.5f }?.let { v -> set(e.copy(validationShare = v)) } }
            NumberField(Res.string.train_test_share, AiTerm.TEST_ACCURACY, e.testShare.toString()) { it.toFloatOrNull()?.takeIf { v -> v in 0f..0.5f }?.let { v -> set(e.copy(testShare = v)) } }
            NumberField(Res.string.train_seed, AiTerm.SEED, e.seed.toString()) { it.toIntOrNull()?.let { v -> set(e.copy(seed = v)) } }
            NumberField(Res.string.train_hidden_units, AiTerm.HIDDEN_LAYER, e.hiddenUnits.toString()) { it.toIntOrNull()?.takeIf { v -> v >= 0 }?.let { v -> set(e.copy(hiddenUnits = v)) } }
            NumberField(Res.string.train_replay_per_class, AiTerm.CONTINUAL, e.replayPerClass.toString()) { it.toIntOrNull()?.takeIf { v -> v > 0 }?.let { v -> set(e.copy(replayPerClass = v)) } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.train_class_balanced), Modifier.weight(1f))
                InfoTip(AiTerm.CLASS_BALANCE)
                Switch(e.classBalanced, { set(e.copy(classBalanced = it)) })
            }
        }
        Button(onClick = { onAction(TrainingAction.SaveSettings(settings)) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.train_save)) }
    }
}

/** A number setting; [onValue] gets the text when it changes and decides if it's valid. */
@Composable
private fun NumberField(label: StringResource, term: AiTerm, initial: String, onValue: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    OutlinedTextField(
        text,
        { text = it; onValue(it) },
        label = { Text(stringResource(label)) },
        trailingIcon = { InfoTip(term) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun UseScreen(state: TrainingState, onAction: (TrainingAction) -> Unit, platform: TrainingPlatform, viewModel: TrainingViewModel?) {
    val model = state.model ?: return LoadingState()
    val spacing = HerbLensTheme.spacing
    Page(model.name, onBack = { onAction(TrainingAction.Back) }) {
        val camera = platform.camera
        val photo = state.photo
        val view = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))
        when {
            photo != null -> RemoteImage(photo, contentDescription = null, modifier = view)
            camera != null && viewModel != null -> camera({ image, _ -> viewModel.classify(image) }, view)
            else -> Text(stringResource(Res.string.train_use_hint), style = MaterialTheme.typography.bodyMedium)
        }
        state.predictions.forEach { (species, probability) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).padding(vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(species, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(percent(probability), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            OutlinedButton(
                onClick = { platform.pickPhotos(PhotoPick { photos -> photos.firstOrNull()?.let { onAction(TrainingAction.ClassifyPhoto(it)) } }) },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(Res.string.train_use_pick_photo)) }
            if (photo != null && camera != null) {
                OutlinedButton(onClick = { onAction(TrainingAction.BackToCamera) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.train_use_camera))
                }
            }
        }
        SpeciesList(model.trainedClasses)
        if (model.imported) {
            // Why there's no training here: the file doesn't carry what's needed to learn more
            Text(stringResource(Res.string.train_use_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** What a model can name, so whoever has it (from a friend, say) knows what it can and can't identify. */
@Composable
private fun SpeciesList(species: List<String>) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) { Text(stringResource(Res.string.train_knows_species, species.size)) }
    if (!open) return
    Text(stringResource(Res.string.train_knows_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    species.sortedBy { it.lowercase() }.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
}

private fun percent(value: Float) = "${(value * 100).roundToInt()}%"
