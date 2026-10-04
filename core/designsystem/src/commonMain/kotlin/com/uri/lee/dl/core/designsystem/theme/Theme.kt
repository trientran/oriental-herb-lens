package com.uri.lee.dl.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing steps used everywhere instead of ad-hoc numbers. */
@Immutable
data class Spacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    /** Content on wide windows stops growing at this width. */
    val maxContentWidth: Dp = 720.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

private val HerbLensShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * The app theme. Dynamic colour is deliberately not used, so herb photos always sit on the
 * brand colours.
 */
@Composable
fun HerbLensTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = herbLensTypography(),
        shapes = HerbLensShapes,
        content = content,
    )
}

object HerbLensTheme {
    val spacing: Spacing
        @Composable get() = LocalSpacing.current
}
