package com.uri.lee.dl.core.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.resources.term_ai_model
import com.uri.lee.dl.core.designsystem.resources.term_ai_model_body
import com.uri.lee.dl.core.designsystem.resources.term_base_model
import com.uri.lee.dl.core.designsystem.resources.term_base_model_body
import com.uri.lee.dl.core.designsystem.resources.term_batch_size
import com.uri.lee.dl.core.designsystem.resources.term_batch_size_body
import com.uri.lee.dl.core.designsystem.resources.term_checking_photos
import com.uri.lee.dl.core.designsystem.resources.term_checking_photos_body
import com.uri.lee.dl.core.designsystem.resources.term_class_balance
import com.uri.lee.dl.core.designsystem.resources.term_class_balance_body
import com.uri.lee.dl.core.designsystem.resources.term_confidence
import com.uri.lee.dl.core.designsystem.resources.term_confidence_body
import com.uri.lee.dl.core.designsystem.resources.term_confusion
import com.uri.lee.dl.core.designsystem.resources.term_confusion_body
import com.uri.lee.dl.core.designsystem.resources.term_continual
import com.uri.lee.dl.core.designsystem.resources.term_continual_body
import com.uri.lee.dl.core.designsystem.resources.term_early_stopping
import com.uri.lee.dl.core.designsystem.resources.term_early_stopping_body
import com.uri.lee.dl.core.designsystem.resources.term_hidden_layer
import com.uri.lee.dl.core.designsystem.resources.term_hidden_layer_body
import com.uri.lee.dl.core.designsystem.resources.term_hugging_face
import com.uri.lee.dl.core.designsystem.resources.term_hugging_face_body
import com.uri.lee.dl.core.designsystem.resources.term_identify_modes
import com.uri.lee.dl.core.designsystem.resources.term_identify_modes_body
import com.uri.lee.dl.core.designsystem.resources.term_image_classification
import com.uri.lee.dl.core.designsystem.resources.term_image_classification_body
import com.uri.lee.dl.core.designsystem.resources.term_learning_rate
import com.uri.lee.dl.core.designsystem.resources.term_learning_rate_body
import com.uri.lee.dl.core.designsystem.resources.term_loss
import com.uri.lee.dl.core.designsystem.resources.term_loss_body
import com.uri.lee.dl.core.designsystem.resources.term_round
import com.uri.lee.dl.core.designsystem.resources.term_round_body
import com.uri.lee.dl.core.designsystem.resources.term_seed
import com.uri.lee.dl.core.designsystem.resources.term_seed_body
import com.uri.lee.dl.core.designsystem.resources.term_test_accuracy
import com.uri.lee.dl.core.designsystem.resources.term_test_accuracy_body
import com.uri.lee.dl.core.designsystem.resources.term_tflite
import com.uri.lee.dl.core.designsystem.resources.term_tflite_body
import com.uri.lee.dl.core.designsystem.resources.term_training
import com.uri.lee.dl.core.designsystem.resources.term_training_body
import com.uri.lee.dl.core.designsystem.resources.term_weight_decay
import com.uri.lee.dl.core.designsystem.resources.term_weight_decay_body
import com.uri.lee.dl.core.designsystem.resources.tip_got_it
import com.uri.lee.dl.core.designsystem.resources.tip_open
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The AI terms the app explains to people new to AI: a name and a plain explanation each. */
enum class AiTerm(val title: StringResource, val body: StringResource) {
    AI_MODEL(Res.string.term_ai_model, Res.string.term_ai_model_body),
    IMAGE_CLASSIFICATION(Res.string.term_image_classification, Res.string.term_image_classification_body),
    CONFIDENCE(Res.string.term_confidence, Res.string.term_confidence_body),
    IDENTIFY_MODES(Res.string.term_identify_modes, Res.string.term_identify_modes_body),
    BASE_MODEL(Res.string.term_base_model, Res.string.term_base_model_body),
    TRAINING(Res.string.term_training, Res.string.term_training_body),
    ROUND(Res.string.term_round, Res.string.term_round_body),
    CHECKING_PHOTOS(Res.string.term_checking_photos, Res.string.term_checking_photos_body),
    TEST_ACCURACY(Res.string.term_test_accuracy, Res.string.term_test_accuracy_body),
    LOSS(Res.string.term_loss, Res.string.term_loss_body),
    CONFUSION(Res.string.term_confusion, Res.string.term_confusion_body),
    CONTINUAL(Res.string.term_continual, Res.string.term_continual_body),
    LEARNING_RATE(Res.string.term_learning_rate, Res.string.term_learning_rate_body),
    BATCH_SIZE(Res.string.term_batch_size, Res.string.term_batch_size_body),
    EARLY_STOPPING(Res.string.term_early_stopping, Res.string.term_early_stopping_body),
    WEIGHT_DECAY(Res.string.term_weight_decay, Res.string.term_weight_decay_body),
    SEED(Res.string.term_seed, Res.string.term_seed_body),
    HIDDEN_LAYER(Res.string.term_hidden_layer, Res.string.term_hidden_layer_body),
    CLASS_BALANCE(Res.string.term_class_balance, Res.string.term_class_balance_body),
    TFLITE(Res.string.term_tflite, Res.string.term_tflite_body),
    HUGGING_FACE(Res.string.term_hugging_face, Res.string.term_hugging_face_body),
}

/** A small question mark beside an AI term; tapping it explains the term in plain words. */
@Composable
fun InfoTip(term: AiTerm, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier.size(32.dp)) {
        Icon(
            Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = stringResource(Res.string.tip_open, stringResource(term.title)),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
    if (open) TermDialog(term) { open = false }
}

/** The term itself as a chip with a question mark, for introductions that name several terms. */
@Composable
fun TermChip(term: AiTerm, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    AssistChip(
        onClick = { open = true },
        label = { Text(stringResource(term.title)) },
        trailingIcon = { Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) },
        modifier = modifier,
    )
    if (open) TermDialog(term) { open = false }
}

@Composable
private fun TermDialog(term: AiTerm, onDismiss: () -> Unit) {
    DialogLayer {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(term.title)) },
            text = { Text(stringResource(term.body), Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.tip_got_it)) } },
        )
    }
}
