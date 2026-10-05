import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import com.uri.lee.dl.core.common.text.PlatformTextNormalizer
import com.uri.lee.dl.data.catalog.SpeciesCsvReader
import com.uri.lee.dl.domain.search.SpeciesSearchIndex
import org.jetbrains.compose.resources.Font
import spike.resources.Res
import spike.resources.be_vietnam_pro_italic
import spike.resources.be_vietnam_pro_regular
import spike.resources.be_vietnam_pro_semi_bold
import kotlin.time.TimeSource

private val started = TimeSource.Monotonic.markNow()

private object SpikeTimings {
    var catalogReadyMs: Int = -1
    var species: Int = 0
    var lastSearchMs: Int = -1
}

/** Read from the browser console: when the catalog was ready, how many species, the last search's time. */
@JsExport
@OptIn(kotlin.js.ExperimentalJsExport::class)
fun spikeTimings(): String = "${SpikeTimings.catalogReadyMs},${SpikeTimings.species},${SpikeTimings.lastSearchMs}"

/** App Check, Firestore, Google sign-in and the photo Worker, from the browser. */
@Composable
expect fun FirebaseChecks()

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport("compose") { Spike() }
}

@Composable
private fun Spike() {
    val fonts = FontFamily(
        Font(Res.font.be_vietnam_pro_regular),
        Font(Res.font.be_vietnam_pro_semi_bold, FontWeight.SemiBold),
        Font(Res.font.be_vietnam_pro_italic, style = FontStyle.Italic),
    )
    val base = Typography()
    val typography = Typography(
        bodyLarge = base.bodyLarge.copy(fontFamily = fonts),
        bodyMedium = base.bodyMedium.copy(fontFamily = fonts),
        titleMedium = base.titleMedium.copy(fontFamily = fonts, fontWeight = FontWeight.SemiBold),
    )
    var index by remember { mutableStateOf<SpeciesSearchIndex?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val text = Res.readBytes("files/herb_catalog.csv").decodeToString()
        val species = SpeciesCsvReader(PlatformTextNormalizer).read(text).species
        index = SpeciesSearchIndex(species)
        SpikeTimings.species = species.size
        SpikeTimings.catalogReadyMs = started.elapsedNow().inWholeMilliseconds.toInt()
    }
    MaterialTheme(typography = typography) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Herb Lens web spike", style = MaterialTheme.typography.titleMedium)
                FirebaseChecks()
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Tìm cây thuốc (Vietnamese or Latin)") })
                val current = index
                if (current == null) {
                    Text("Loading the catalog…")
                } else {
                    val mark = TimeSource.Monotonic.markNow()
                    val matches = remember(query, current) { if (query.isBlank()) emptyList() else current.search(query, 30) }
                    SpikeTimings.lastSearchMs = mark.elapsedNow().inWholeMilliseconds.toInt()
                    Text("${SpikeTimings.species} species, ready in ${SpikeTimings.catalogReadyMs} ms", style = MaterialTheme.typography.bodyMedium)
                    // Compose text isn't selectable unless it's in a SelectionContainer
                    SelectionContainer {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(matches) { match ->
                                Column {
                                    Text(match.species.preferredVietnameseName ?: match.species.scientificName, style = MaterialTheme.typography.bodyLarge)
                                    Text(match.species.scientificName, style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
