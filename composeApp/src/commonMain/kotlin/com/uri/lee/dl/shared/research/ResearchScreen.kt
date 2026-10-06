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
import androidx.compose.material.icons.filled.Close
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
import com.uri.lee.dl.core.training.Strategy
import com.uri.lee.dl.core.training.TrainingOptions
import kotlinx.coroutines.launch

private val STRATEGIES = listOf(
    Strategy.Joint, Strategy.Naive, Strategy.Replay(5), Strategy.Replay(10), Strategy.Replay(20), Strategy.Replay(50), Strategy.Prototypes,
)

/**
 * Research mode (plan Phase 7; hidden: tap the version on Profile 7 times; for researchers, so not
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
                        Section(if (pending.finished) "Last run: finished" else "Last run: stopped") {
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

                    Section("Dataset") {
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
                            Text("${d.name}: ${d.images.size} photos, ${d.classes.size} classes", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                d.classes.joinToString("\n") { c -> "$c: ${d.images.count { it.className == c }}" },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    Section("Backbones") {
                        Text("Downloaded from MediaPipe when first used, then kept.", style = MaterialTheme.typography.bodySmall)
                        Chips(state.backbones, backbones, { it }) { backbones = it }
                    }
                    Section("Scenarios (5 steps)") {
                        Chips(ScenarioKind.entries, scenarios, { it.id }) { scenarios = it }
                    }
                    Section("Strategies") {
                        Chips(STRATEGIES, strategies, { it.id }) { strategies = it }
                    }
                    Section("Seeds: 1 to ${seeds.toInt()}") {
                        Slider(seeds, { seeds = it }, valueRange = 1f..10f, steps = 8, enabled = !state.running)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Switch(hiddenLayer, { hiddenLayer = it }, enabled = !state.running)
                        Text("Hidden layer of 100 units (much slower)", style = MaterialTheme.typography.bodyMedium)
                    }

                    val plan = ResearchPlan(
                        scenarios = ScenarioKind.entries.filter { it in scenarios }.map { Scenario(it) },
                        strategies = STRATEGIES.filter { it in strategies },
                        seeds = (1..seeds.toInt()).toList(),
                        options = TrainingOptions(classBalanced = true, hiddenUnits = if (hiddenLayer) 100 else 0),
                    )
                    val chosen = state.backbones.filter { it in backbones }
                    Text("${plan.runsPerBackbone * chosen.size} runs", style = MaterialTheme.typography.bodySmall)

                    if (controller.backgroundNote.isNotEmpty()) Text(controller.backgroundNote, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.running) {
                            Button(onClick = controller::stop) { Text("Stop") }
                        } else {
                            Button(
                                onClick = { controller.start(plan, chosen) },
                                enabled = state.dataset != null && chosen.isNotEmpty() && plan.runsPerBackbone > 0,
                            ) { Text(if (state.pending != null) "Start a new run" else "Start") }
                        }
                        TextButton(onClick = onDismiss) { Text("Close") }
                    }

                    if (state.status.isNotEmpty()) Text(state.status, style = MaterialTheme.typography.bodyMedium)
                    state.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    if (state.summary.isNotEmpty()) {
                        Section("Results" + if (state.running) " so far" else "") {
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
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
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
