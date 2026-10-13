package com.uri.lee.dl.shared.research

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uri.lee.dl.core.training.ResearchPlan
import com.uri.lee.dl.core.training.Scenario
import com.uri.lee.dl.core.training.ScenarioKind
import com.uri.lee.dl.core.training.SplitSettings
import com.uri.lee.dl.core.training.Strategy
import com.uri.lee.dl.core.training.TrainingOptions
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val STRATEGIES = listOf(
    Strategy.Joint, Strategy.Naive, Strategy.LwF, Strategy.Replay(5), Strategy.Replay(10), Strategy.Replay(20), Strategy.Replay(50), Strategy.Prototypes,
)

/**
 * Research mode (plan Phase 7; in Profile; for researchers, so not
 * translated): choose a dataset and what to run, leave the device running, then save one zip with
 * every result and the models.
 */
@Composable
fun ResearchDialog(controller: ResearchController, onDismiss: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var backbones by remember { mutableStateOf(setOf<String>()) }
    var scenarios by remember { mutableStateOf(ScenarioKind.entries.toSet()) }
    var strategies by remember { mutableStateOf(STRATEGIES.toSet()) }
    var seeds by remember { mutableStateOf(10f) }
    var hiddenLayer by remember { mutableStateOf(false) }
    var separateTestSet by remember { mutableStateOf(false) }
    var trainPercent by remember { mutableStateOf(70f) }
    var validationPercent by remember { mutableStateOf(15f) }

    LaunchedEffect(Unit) { controller.refresh() }
    LaunchedEffect(state.backbones) { if (backbones.isEmpty()) backbones = state.backbones.toSet() }

    // Closing the screen doesn't stop a run: it carries on, and the screen shows it when reopened
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                // Always on screen: the way out, whatever is scrolled
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                    Text("Research mode", style = MaterialTheme.typography.titleLarge)
                    HelpButton(ResearchHelp.overview)
                }
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp).widthIn(max = 720.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Runs the study's scenarios, strategies and seeds on this device and saves every measurement as CSV, " +
                            "with the trained models, in one zip.",
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    state.pending?.takeIf { !state.running }?.let { pending ->
                        Section(if (pending.finished) "Last run: finished" else "Last run: stopped", ResearchHelp.lastRun) {
                            Text(
                                "${pending.dataset}: ${pending.done} of ${pending.total} runs done. It's kept on this device until you start a new run or discard it.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (pending.finished) {
                                    Button(onClick = { scope.launch { controller.save() } }) { Text("Save results") }
                                } else {
                                    Button(onClick = controller::resume) { Text("Resume") }
                                    OutlinedButton(onClick = { scope.launch { controller.save() } }) { Text("Save results so far") }
                                }
                                TextButton(onClick = controller::discard) { Text("Discard") }
                            }
                        }
                    }

                    Section("Dataset", ResearchHelp.dataset) {
                        Text(
                            "A folder with one subfolder of photos per species." +
                                if (controller.canPickZip) " If no folders show up (some phones), choose a zip instead." else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { scope.launch { controller.pick(zip = false) } }, enabled = !state.running) { Text("Choose folder") }
                            if (controller.canPickZip) {
                                OutlinedButton(onClick = { scope.launch { controller.pick(zip = true) } }, enabled = !state.running) { Text("Choose zip") }
                            }
                        }
                        if (state.appDatasets.isNotEmpty()) {
                            Text("In the app's folder:", style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.appDatasets.forEach { d ->
                                    FilterChip(
                                        selected = state.dataset === d,
                                        onClick = { controller.choose(d) },
                                        enabled = !state.running,
                                        label = { Text("${d.name} (${d.images.size})") },
                                    )
                                }
                            }
                        }
                        state.dataset?.let { d ->
                            Text(
                                "${d.name}: ${d.images.size} photos, ${d.classes.size} classes" + (d.stages?.let { ", ${it.size} stages" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                d.classes.joinToString("\n") { c ->
                                    "$c: ${d.images.count { it.className == c }}" + (d.stageOf(c)?.let { " (${d.stages!![it]})" } ?: "")
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    Section("Test photos", ResearchHelp.testPhotos) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(!separateTestSet, { separateTestSet = false }, label = { Text("Held out of the dataset") }, enabled = !state.running)
                            FilterChip(separateTestSet, { separateTestSet = true }, label = { Text("A separate test set") }, enabled = !state.running)
                        }
                        if (!separateTestSet) {
                            val test = 100 - trainPercent.roundToInt() - validationPercent.roundToInt()
                            Text(
                                "Each class is split at random, per seed: ${trainPercent.roundToInt()} % to train, " +
                                    "${validationPercent.roundToInt()} % to decide when to stop, $test % to test.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text("Training: ${trainPercent.roundToInt()} %", style = MaterialTheme.typography.bodyMedium)
                            Slider(trainPercent, { trainPercent = it }, valueRange = 50f..90f, steps = 7, enabled = !state.running)
                            Text("Validation: ${validationPercent.roundToInt()} %", style = MaterialTheme.typography.bodyMedium)
                            Slider(validationPercent, { validationPercent = it }, valueRange = 5f..30f, steps = 4, enabled = !state.running)
                            if (test < 5) Text("Leave at least 5 % to test.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text(
                                "Test on other photos of the same classes, e.g. from another phone, site or season: a folder with " +
                                    "the same class folders. The dataset is then split into training and validation only.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { scope.launch { controller.pick(zip = false, test = true) } }, enabled = !state.running) { Text("Choose folder") }
                                if (controller.canPickZip) {
                                    OutlinedButton(onClick = { scope.launch { controller.pick(zip = true, test = true) } }, enabled = !state.running) { Text("Choose zip") }
                                }
                            }
                            val others = state.appDatasets.filter { it !== state.dataset }
                            if (others.isNotEmpty()) {
                                Text("In the app's folder:", style = MaterialTheme.typography.bodySmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    others.forEach { d ->
                                        FilterChip(
                                            selected = state.testDataset === d,
                                            onClick = { controller.chooseTest(d) },
                                            enabled = !state.running,
                                            label = { Text("${d.name} (${d.images.size})") },
                                        )
                                    }
                                }
                            }
                            state.testDataset?.let { t ->
                                Text("${t.name}: ${t.images.size} photos, ${t.classes.size} classes", style = MaterialTheme.typography.bodyMedium)
                                state.dataset?.takeIf { it.classes != t.classes }?.let {
                                    Text("Its classes don't match the dataset's.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Text("Validation: ${validationPercent.roundToInt()} % of the dataset, to decide when to stop", style = MaterialTheme.typography.bodyMedium)
                            Slider(validationPercent, { validationPercent = it }, valueRange = 5f..30f, steps = 4, enabled = !state.running)
                        }
                    }

                    Section("Backbones", ResearchHelp.backbones) {
                        Text("Downloaded from MediaPipe when first used, then kept.", style = MaterialTheme.typography.bodySmall)
                        Chips(state.backbones, backbones, { it }) { backbones = it }
                    }
                    val stages = state.dataset?.stages
                    // Stage-incremental needs stage folders
                    val kinds = ScenarioKind.entries.filter { it != ScenarioKind.StageIncremental || stages != null }
                    Section("Scenarios", ResearchHelp.scenarios) {
                        Text(
                            "Class- and data-incremental runs have 5 steps" +
                                (stages?.let { "; stage-incremental, one per stage: ${it.joinToString(", then ")}" }
                                    ?: ". Group the class folders into stage folders (\"1 Vietnam\", \"2 Australia\") to learn them in that order."),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Chips(kinds, scenarios, { it.id }) { scenarios = it }
                    }
                    Section("Strategies", ResearchHelp.strategies) {
                        Chips(STRATEGIES, strategies, { it.id }) { strategies = it }
                    }
                    Section("Seeds: 1 to ${seeds.toInt()}", ResearchHelp.seeds) {
                        Slider(seeds, { seeds = it }, valueRange = 1f..10f, steps = 8, enabled = !state.running)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Switch(hiddenLayer, { hiddenLayer = it }, enabled = !state.running)
                        Text("Hidden layer of 100 units (much slower)", style = MaterialTheme.typography.bodyMedium)
                        HelpButton(ResearchHelp.hiddenLayer)
                    }

                    val validationShare = validationPercent.roundToInt() / 100f
                    // Kept valid while the sliders add up to too much (Start is off then)
                    val trainShare = if (separateTestSet) 1f - validationShare else minOf(trainPercent.roundToInt() / 100f, 1f - validationShare)
                    val splitValid = separateTestSet || trainPercent.roundToInt() + validationPercent.roundToInt() <= 95
                    val plan = ResearchPlan(
                        scenarios = kinds.filter { it in scenarios }.map { if (it == ScenarioKind.StageIncremental) Scenario(it, stages!!.size) else Scenario(it) },
                        strategies = STRATEGIES.filter { it in strategies },
                        seeds = (1..seeds.toInt()).toList(),
                        options = TrainingOptions(classBalanced = true, hiddenUnits = if (hiddenLayer) 100 else 0),
                        split = SplitSettings(trainShare, validationShare),
                    )
                    val chosen = state.backbones.filter { it in backbones }
                    Text("${plan.runsPerBackbone * chosen.size} runs", style = MaterialTheme.typography.bodySmall)

                    if (controller.backgroundNote.isNotEmpty()) Text(controller.backgroundNote, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.running) {
                            Button(onClick = controller::stop) { Text("Stop") }
                        } else {
                            Button(
                                onClick = { controller.start(plan, chosen, useTestSet = separateTestSet) },
                                enabled = state.dataset != null && chosen.isNotEmpty() && plan.runsPerBackbone > 0 && splitValid &&
                                    (!separateTestSet || state.testDataset != null),
                            ) { Text(if (state.pending != null) "Start a new run" else "Start") }
                        }
                        TextButton(onClick = onDismiss) { Text("Close") }
                    }

                    if (state.status.isNotEmpty()) Text(state.status, style = MaterialTheme.typography.bodyMedium)
                    state.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    if (state.summary.isNotEmpty()) {
                        Section("Results" + if (state.running) " so far" else "", ResearchHelp.results) {
                            ResearchResultsView(state.summary)
                        }
                    }
                    if (state.log.isNotEmpty()) {
                        // Its own selection: the page's doesn't reach into a dialog
                        SelectionContainer { Text(state.log.joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, help: Help? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            help?.let { HelpButton(it) }
        }
        content()
    }
}

/** A help icon that explains [help] in a dialog. */
@Composable
private fun HelpButton(help: Help) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = "About ${help.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(help.title) },
            text = { Text(help.text, Modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { open = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun <T> Chips(options: List<T>, selected: Set<T>, label: (T) -> String, onChange: (Set<T>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option in selected,
                onClick = { onChange(if (option in selected) selected - option else selected + option) },
                label = { Text(label(option)) },
            )
        }
    }
}
