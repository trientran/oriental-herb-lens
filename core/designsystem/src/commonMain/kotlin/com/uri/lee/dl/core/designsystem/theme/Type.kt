package com.uri.lee.dl.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.resources.be_vietnam_pro_bold
import com.uri.lee.dl.core.designsystem.resources.be_vietnam_pro_italic
import com.uri.lee.dl.core.designsystem.resources.be_vietnam_pro_medium
import com.uri.lee.dl.core.designsystem.resources.be_vietnam_pro_medium_italic
import com.uri.lee.dl.core.designsystem.resources.be_vietnam_pro_regular
import com.uri.lee.dl.core.designsystem.resources.be_vietnam_pro_semi_bold
import org.jetbrains.compose.resources.Font

/** Be Vietnam Pro: drawn so stacked Vietnamese marks (ặ, ổ, ự) stay clear at small sizes. */
@Composable
private fun beVietnamPro() = FontFamily(
    Font(Res.font.be_vietnam_pro_regular, FontWeight.Normal),
    Font(Res.font.be_vietnam_pro_italic, FontWeight.Normal, FontStyle.Italic),
    Font(Res.font.be_vietnam_pro_medium, FontWeight.Medium),
    Font(Res.font.be_vietnam_pro_medium_italic, FontWeight.Medium, FontStyle.Italic),
    Font(Res.font.be_vietnam_pro_semi_bold, FontWeight.SemiBold),
    Font(Res.font.be_vietnam_pro_bold, FontWeight.Bold),
)

@Composable
internal fun herbLensTypography(): Typography {
    val family = beVietnamPro()
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontFamily = family, fontWeight = FontWeight.Medium),
    )
}
