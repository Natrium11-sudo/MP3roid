package com.mp3roid.player.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Bewusst nah an den Material-3-Vorgaben: die Systemschriften sind auf einem
 * Einsteigergeräte deutlich billiger zu rendern als eingebettete Schriftarten.
 */
fun mp3roidTypography(): Typography {
    val base = Typography()
    return base.copy(
        displaySmall = base.displaySmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        ),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(lineHeight = 22.sp),
        bodyMedium = base.bodyMedium.copy(lineHeight = 20.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
        labelMedium = base.labelMedium.copy(letterSpacing = 0.3.sp),
        labelSmall = base.labelSmall.copy(letterSpacing = 0.6.sp),
    )
}
