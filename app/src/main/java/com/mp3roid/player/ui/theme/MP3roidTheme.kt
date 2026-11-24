package com.mp3roid.player.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Alle Farb- und Formwerte eines Themes.
 *
 * Ein neues Theme hinzufügen heisst: dieses Datenobjekt ausfüllen. Es geht
 * entweder in Kotlin (siehe [MP3roidThemes]) oder ohne eine Zeile Code als
 * JSON-Datei in `assets/themes/` (siehe [ThemeCatalog]).
 */
@Immutable
data class MP3roidColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val accent: Color,
    val accentSecondary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val gradient: List<Color>,
    val isDark: Boolean,
    val coverCorner: Dp = 22.dp,
    val cardCorner: Dp = 18.dp,
    val controlSize: Dp = 72.dp,
) {
    val onAccent: Color
        get() = if (accent.luminance() > 0.55f) Color(0xFF0C0C10) else Color.White

    val outline: Color get() = textSecondary.copy(alpha = 0.32f)
    val hairline: Color get() = textSecondary.copy(alpha = 0.16f)
    val gradientTop: Color get() = gradient.firstOrNull() ?: accent
    val gradientBottom: Color get() = gradient.lastOrNull() ?: accentSecondary
}

@Immutable
data class MP3roidTheme(
    val id: String,
    val name: String,
    val description: String = "",
    val colors: MP3roidColors,
)

val LocalMP3roidTheme = staticCompositionLocalOf { MP3roidThemes.darkVoid }

/** Übersetzt die eigenen Tokens in das Material-3-Farbschema. */
fun MP3roidTheme.toColorScheme(): ColorScheme {
    val c = colors
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    val onAccentSecondary = if (c.accentSecondary.luminance() > 0.55f) Color(0xFF0C0C10) else Color.White
    return base.copy(
        primary = c.accent,
        onPrimary = c.onAccent,
        primaryContainer = c.accent.copy(alpha = 0.22f),
        onPrimaryContainer = c.textPrimary,
        inversePrimary = c.accent,
        secondary = c.accentSecondary,
        onSecondary = onAccentSecondary,
        secondaryContainer = c.accent.copy(alpha = 0.18f),
        onSecondaryContainer = c.textPrimary,
        tertiary = c.gradientBottom,
        onTertiary = c.textPrimary,
        tertiaryContainer = c.accentSecondary.copy(alpha = 0.18f),
        onTertiaryContainer = c.textPrimary,
        background = c.background,
        onBackground = c.textPrimary,
        surface = c.surface,
        onSurface = c.textPrimary,
        surfaceVariant = c.surfaceElevated,
        onSurfaceVariant = c.textSecondary,
        surfaceTint = c.accent,
        surfaceBright = c.surfaceElevated,
        surfaceDim = c.background,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.surfaceElevated,
        surfaceContainerHighest = c.surfaceElevated,
        surfaceContainerLow = c.surface,
        surfaceContainerLowest = c.background,
        inverseSurface = c.textPrimary,
        inverseOnSurface = c.background,
        error = Color(0xFFFF5252),
        onError = Color.White,
        errorContainer = Color(0x33FF5252),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = c.outline,
        outlineVariant = c.hairline,
        scrim = Color(0xCC000000),
    )
}
