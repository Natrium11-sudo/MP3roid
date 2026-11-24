package com.mp3roid.player.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Mitgelieferte Themes. Reihenfolge = Reihenfolge in den Einstellungen.
 */
object MP3roidThemes {

    val darkVoid = MP3roidTheme(
        id = "dark-void",
        name = "Dark Void",
        description = "Standard · tiefschwarz mit warmem Akzent",
        colors = MP3roidColors(
            background = Color(0xFF07070B),
            surface = Color(0xFF111119),
            surfaceElevated = Color(0xFF1A1A26),
            accent = Color(0xFFFFB74D),
            accentSecondary = Color(0xFF8B5CF6),
            textPrimary = Color(0xFFF2F2F7),
            textSecondary = Color(0xFF8E8EA8),
            gradient = listOf(Color(0xFFFFB74D), Color(0xFF8B5CF6), Color(0xFF241B45)),
            isDark = true,
            coverCorner = 24.dp,
            cardCorner = 18.dp,
            controlSize = 74.dp,
        ),
    )

    val midnight = MP3roidTheme(
        id = "midnight-blue",
        name = "Midnight Blue",
        description = "Kühles Blau, sehr dunkel",
        colors = MP3roidColors(
            background = Color(0xFF05080F),
            surface = Color(0xFF0D1421),
            surfaceElevated = Color(0xFF162034),
            accent = Color(0xFF4DA3FF),
            accentSecondary = Color(0xFF3DDCC0),
            textPrimary = Color(0xFFE8F0FF),
            textSecondary = Color(0xFF7D8CA8),
            gradient = listOf(Color(0xFF4DA3FF), Color(0xFF3DDCC0), Color(0xFF10233F)),
            isDark = true,
        ),
    )

    val amoled = MP3roidTheme(
        id = "amoled-black",
        name = "AMOLED Black",
        description = "Echtes Schwarz · spart Strom",
        colors = MP3roidColors(
            background = Color(0xFF000000),
            surface = Color(0xFF000000),
            surfaceElevated = Color(0xFF101010),
            accent = Color(0xFF64FFDA),
            accentSecondary = Color(0xFFFF6E6E),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFF9A9A9A),
            gradient = listOf(Color(0xFF64FFDA), Color(0xFF009688), Color(0xFF001A16)),
            isDark = true,
            coverCorner = 6.dp,
            cardCorner = 8.dp,
        ),
    )

    val warmAnalog = MP3roidTheme(
        id = "warm-analog",
        name = "Warm Analog",
        description = "Holz, Messing, Kassettendeck-Stimmung",
        colors = MP3roidColors(
            background = Color(0xFF16110C),
            surface = Color(0xFF221A13),
            surfaceElevated = Color(0xFF2E241A),
            accent = Color(0xFFE0A85C),
            accentSecondary = Color(0xFFB5533C),
            textPrimary = Color(0xFFF6EBDD),
            textSecondary = Color(0xFFA8917A),
            gradient = listOf(Color(0xFFE0A85C), Color(0xFFB5533C), Color(0xFF3A2418)),
            isDark = true,
            coverCorner = 4.dp,
            cardCorner = 6.dp,
        ),
    )

    val builtIn: List<MP3roidTheme> = listOf(darkVoid, midnight, amoled, warmAnalog)
}
