package com.uri.lee.dl.feature.contribute

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.resources.cd_close
import com.uri.lee.dl.core.designsystem.resources.contribute_map_hint
import com.uri.lee.dl.core.designsystem.resources.contribute_use_this_place
import com.uri.lee.dl.core.designsystem.theme.HerbLensTheme
import com.uri.lee.dl.core.maps.HerbMap
import com.uri.lee.dl.core.maps.LatLng
import com.uri.lee.dl.domain.model.GeoLocation
import org.jetbrains.compose.resources.stringResource

/**
 * Full-screen map with a pin fixed in the middle: the user drags the map under the pin, which is
 * easier than tapping an exact spot, then confirms.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlacePicker(start: GeoLocation?, onPicked: (GeoLocation) -> Unit, onDismiss: () -> Unit) {
    var center by remember { mutableStateOf(start?.let { LatLng(it.latitude, it.longitude) }) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.contribute_map_hint), style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.cd_close)) }
                    },
                )
            },
            bottomBar = {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { center?.let { onPicked(GeoLocation(it.latitude, it.longitude)) } },
                        enabled = center != null,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(HerbLensTheme.spacing.lg),
                    ) { Text(stringResource(Res.string.contribute_use_this_place)) }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                HerbMap(
                    points = listOfNotNull(start?.let { LatLng(it.latitude, it.longitude) }),
                    modifier = Modifier.fillMaxSize(),
                    interactive = true,
                    onCenterChanged = { center = it },
                    showMarkers = false,
                )
                // The pin's tip marks the centre
                Icon(
                    Icons.Filled.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center).size(48.dp).offset(y = (-24).dp),
                )
            }
        }
    }
}
