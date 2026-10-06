package com.uri.lee.dl.feature.herbdetails

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.resources.cd_next_photo
import com.uri.lee.dl.core.designsystem.resources.cd_previous_photo
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Previous and next buttons over a photo pager, for a mouse or keyboard as well as swiping. */
@Composable
internal fun BoxScope.PagerArrows(pager: PagerState) {
    val scope = rememberCoroutineScope()
    if (pager.currentPage > 0) {
        Arrow(Modifier.align(Alignment.CenterStart)) {
            IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(Res.string.cd_previous_photo), tint = Color.White)
            }
        }
    }
    if (pager.currentPage < pager.pageCount - 1) {
        Arrow(Modifier.align(Alignment.CenterEnd)) {
            IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(Res.string.cd_next_photo), tint = Color.White)
            }
        }
    }
}

@Composable
private fun Arrow(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.padding(8.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape)) { content() }
}
