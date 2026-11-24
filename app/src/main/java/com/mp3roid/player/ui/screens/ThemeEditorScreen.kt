package com.mp3roid.player.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsStateWithLifecycleCompat
import com.mp3roid.player.R
import com.mp3roid.player.ui.MP3roidViewModel
import com.mp3roid.player.ui.components.EmptyState
import com.mp3roid.player.ui.components.MP3roidIconButton
import com.mp3roid.player.ui.theme.LocalMP3roidTheme
import com.mp3roid.player.ui.theme.MP3roidColors
import com.mp3roid.player.ui.theme.MP3roidTheme
import com.mp3roid.player.ui.theme.ThemeCatalog
import kotlin.math.roundToInt

/** Ein bearbeitbares Farbfeld: Beschriftung, Lesen und Schreiben. */
private class ColorSlot(
    val label: String,
    val get: (MP3roidColors) -> Color,
    val set: (MP3roidColors, Color) -> MP3roidColors,
)

private val surfaceSlots = listOf(
    ColorSlot("Hintergrund", { it.background }, { c, v -> c.copy(background = v) }),
    ColorSlot("Fläche", { it.surface }, { c, v -> c.copy(surface = v) }),
    ColorSlot("Erhöhte Fläche", { it.surfaceElevated }, { c, v -> c.copy(surfaceElevated = v) }),
)

private val accentSlots = listOf(
    ColorSlot("Akzent", { it.accent }, { c, v -> c.copy(accent = v) }),
    ColorSlot("Zweitakzent", { it.accentSecondary }, { c, v -> c.copy(accentSecondary = v) }),
)

private val textSlots = listOf(
    ColorSlot("Text", { it.textPrimary }, { c, v -> c.copy(textPrimary = v) }),
    ColorSlot("Nebentext", { it.textSecondary }, { c, v -> c.copy(textSecondary = v) }),
)

// Ein Verlauf darf aus drei Farben bestehen (so kommt „Neon Night" mit). Hier
// werden nur die beiden äußeren bearbeitet; eine Mittelfarbe bleibt erhalten.
private val gradientSlots = listOf(
    ColorSlot(
        "Verlauf oben",
        { it.gradientTop },
        { c, v ->
            c.copy(
                gradient = if (c.gradient.isEmpty()) {
                    listOf(v)
                } else {
                    listOf(v) + c.gradient.drop(1)
                },
            )
        },
    ),
    ColorSlot(
        "Verlauf unten",
        { it.gradientBottom },
        { c, v ->
            c.copy(
                gradient = if (c.gradient.size < 2) {
                    listOf(c.gradient.firstOrNull() ?: v, v)
                } else {
                    c.gradient.dropLast(1) + v
                },
            )
        },
    ),
)

/**
 * Der Editor für selbst gebaute Themes.
 *
 * Bearbeitet wird das gerade gewählte Theme. Weil die ganze Oberfläche am
 * Theme hängt, färbt sie sich beim Ziehen der Regler sofort mit – der Editor
 * ist damit seine eigene Vorschau.
 *
 * Der Name wird nicht getippt: er entsteht beim Anlegen („Eigenes Theme 1", …).
 */
@Composable
fun ThemeEditorScreen(vm: MP3roidViewModel, onBack: () -> Unit) {
    val theme by vm.theme.collectAsStateWithLifecycleCompat()
    val colors = theme.colors

    // Stand beim Betreten – ein „Zurücksetzen" macht das Ausprobieren gefahrlos.
    val initial = remember { theme }

    var picking by remember { mutableStateOf<ColorSlot?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Immer vom aktuell gültigen Theme aus rechnen: sonst ginge beim schnellen
    // Ziehen eine Änderung verloren, wenn zwei Ereignisse in dieselbe
    // Recomposition fallen.
    fun apply(change: (MP3roidColors) -> MP3roidColors) {
        val current = vm.theme.value
        vm.updateTheme(current.copy(colors = change(current.colors)))
    }

    // Sicherheitsnetz, falls der Editor über die Rückgeste verlassen wird.
    DisposableEffect(Unit) {
        onDispose { vm.persistTheme(vm.theme.value) }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = theme.name,
            onBack = {
                vm.persistTheme(theme)
                onBack()
            },
            trailing = {
                // Löschen sitzt oben rechts: dort ist es immer erreichbar, egal
                // wie lang die Reglerliste ist.
                MP3roidIconButton(
                    iconRes = R.drawable.ic_delete,
                    contentDescription = "Theme löschen",
                    onClick = { confirmDelete = true },
                )
            },
        )

        if (!ThemeCatalog.isOwn(theme.id)) {
            EmptyState(
                iconRes = R.drawable.ic_tune,
                title = "Kein eigenes Theme",
                hint = "Dieser Editor gehört zu den selbst gebauten Themes. " +
                    "In den Einstellungen legt „Eigenes\" eine Kopie zum Bearbeiten an.",
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            // Großzügiger Abstand unten: die letzte Zeile soll auch auf
            // 720×1600-Geräten vollständig erreichbar sein.
            contentPadding = PaddingValues(bottom = 128.dp),
        ) {
            item { ThemePreview(theme) }

            colorSlotSection("Flächen", surfaceSlots, colors) { picking = it }
            colorSlotSection("Akzente", accentSlots, colors) { picking = it }
            colorSlotSection("Text", textSlots, colors) { picking = it }
            colorSlotSection("Verlauf", gradientSlots, colors) { picking = it }

            item { SectionLabel("Form") }
            item {
                ShapeSlider("Cover-Ecke", colors.coverCorner.value, 0f..40f) {
                    apply { c -> c.copy(coverCorner = it.roundToInt().dp) }
                }
            }
            item {
                ShapeSlider("Karten-Ecke", colors.cardCorner.value, 0f..30f) {
                    apply { c -> c.copy(cardCorner = it.roundToInt().dp) }
                }
            }
            item {
                ShapeSlider("Reglergröße", colors.controlSize.value, 56f..96f) {
                    apply { c -> c.copy(controlSize = it.roundToInt().dp) }
                }
            }
            item { SectionLabel("Dieses Theme") }
            item {
                SettingRow(
                    iconRes = R.drawable.ic_refresh,
                    title = "Auf Startwert zurücksetzen",
                    subtitle = "Stand beim Öffnen des Editors",
                    onClick = { apply { initial.colors } },
                )
            }
        }
    }

    picking?.let { slot ->
        ColorPickerDialog(
            title = slot.label,
            start = slot.get(colors),
            onChange = { picked -> apply { slot.set(it, picked) } },
            onDismiss = {
                picking = null
                vm.persistTheme(vm.theme.value)
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Theme löschen?") },
            text = {
                Text(
                    "„${theme.name}\u201C wird entfernt. Danach gilt wieder „Dark Void\u201C. " +
                        "Die mitgelieferten Themes bleiben unberührt.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        vm.deleteOwnTheme(theme.id)
                        onBack()
                    },
                ) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") }
            },
            containerColor = LocalMP3roidTheme.current.colors.surfaceElevated,
        )
    }
}

private fun LazyListScope.colorSlotSection(
    title: String,
    slots: List<ColorSlot>,
    colors: MP3roidColors,
    onPick: (ColorSlot) -> Unit,
) {
    item { SectionLabel(title) }
    items(slots) { slot ->
        ColorSlotRow(
            label = slot.label,
            color = slot.get(colors),
            onClick = { onPick(slot) },
        )
    }
}

/**
 * Eine kleine Vorschau der wichtigsten Flächen: Verlauf, Cover-Ecke, Text,
 * Akzent und die Größe des runden Reglers – damit man auch die Formwerte sieht.
 */
@Composable
private fun ThemePreview(theme: MP3roidTheme) {
    val c = theme.colors
    val onSecondary = if (c.accentSecondary.luminance() > 0.55f) Color(0xFF0C0C10) else Color.White

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(c.cardCorner.coerceAtMost(26.dp)))
            .background(Brush.linearGradient(listOf(c.gradientTop, c.gradientBottom)))
            .padding(14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(c.coverCorner.coerceAtMost(23.dp)))
                        .background(c.surface),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Titel",
                        style = MaterialTheme.typography.titleMedium,
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Interpret",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size((c.controlSize / 2).coerceIn(24.dp, 48.dp))
                        .clip(CircleShape)
                        .background(c.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "▶",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.onAccent,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(c.accentSecondary)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = "Zweitfarbe",
                        style = MaterialTheme.typography.labelSmall,
                        color = onSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSlotRow(label: String, color: Color, onClick: () -> Unit) {
    val theme = LocalMP3roidTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = theme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = ThemeCatalog.hexOf(color),
            style = MaterialTheme.typography.labelSmall,
            color = theme.colors.textSecondary,
        )
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(color)
                .border(1.dp, theme.colors.hairline, RoundedCornerShape(9.dp)),
        )
    }
}

@Composable
private fun ShapeSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    val theme = LocalMP3roidTheme.current
    Column(Modifier.padding(horizontal = 18.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = theme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${value.roundToInt()}",
                style = MaterialTheme.typography.labelMedium,
                color = theme.colors.textSecondary,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = theme.colors.accent,
                activeTrackColor = theme.colors.accent,
                inactiveTrackColor = theme.colors.hairline,
            ),
        )
    }
}

/**
 * Farbwahl über Farbton, Sättigung und Helligkeit.
 *
 * Die drei Werte werden einmal vom aktuellen Farbfeld abgeleitet und bleiben
 * danach stehen: sonst wäre der Farbton verloren, sobald man die Sättigung auf
 * null zieht.
 */
@Composable
private fun ColorPickerDialog(
    title: String,
    start: Color,
    onChange: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalMP3roidTheme.current

    // Bewusst ohne Schlüssel: die drei Werte werden beim Öffnen einmal vom
    // Farbfeld abgeleitet und bleiben dann stehen.
    var hue by remember { mutableFloatStateOf(hsvOf(start)[0]) }
    var saturation by remember { mutableFloatStateOf(hsvOf(start)[1]) }
    var brightness by remember { mutableFloatStateOf(hsvOf(start)[2]) }

    val current = Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)))
    val onCurrent = if (current.luminance() > 0.55f) Color(0xFF0C0C10) else Color.White

    fun emit(h: Float = hue, s: Float = saturation, v: Float = brightness) {
        onChange(Color(AndroidColor.HSVToColor(floatArrayOf(h, s, v))))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(current)
                        .border(1.dp, theme.colors.hairline, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = ThemeCatalog.hexOf(current),
                        style = MaterialTheme.typography.labelMedium,
                        color = onCurrent,
                    )
                }
                Spacer(Modifier.height(14.dp))
                QuickColors(current) { picked ->
                    val hsv = hsvOf(picked)
                    hue = hsv[0]
                    saturation = hsv[1]
                    brightness = hsv[2]
                    emit(hsv[0], hsv[1], hsv[2])
                }
                Spacer(Modifier.height(14.dp))
                ChannelSlider(
                    label = "Farbton",
                    value = hue,
                    range = 0f..360f,
                    valueText = "${hue.roundToInt()}°",
                    swatch = current,
                ) { hue = it; emit(h = it) }
                ChannelSlider(
                    label = "Sättigung",
                    value = saturation,
                    range = 0f..1f,
                    valueText = "${(saturation * 100).roundToInt()} %",
                    swatch = current,
                ) { saturation = it; emit(s = it) }
                ChannelSlider(
                    label = "Helligkeit",
                    value = brightness,
                    range = 0f..1f,
                    valueText = "${(brightness * 100).roundToInt()} %",
                    swatch = current,
                ) { brightness = it; emit(v = it) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fertig") }
        },
        containerColor = theme.colors.surfaceElevated,
    )
}

/** Farbton, Sättigung und Helligkeit einer Farbe (0–360, 0–1, 0–1). */
private fun hsvOf(color: Color): FloatArray =
    FloatArray(3).also { AndroidColor.colorToHSV(color.toArgb(), it) }

/**
 * Fertige Farben als Abkürzung. Ein Tipp setzt die drei Regler auf diese Farbe;
 * zum Feinjustieren bleiben sie darunter stehen.
 */
private val QUICK_COLORS = listOf(
    Color(0xFFFF4DB2),
    Color(0xFFEA4DFF),
    Color(0xFF7C5CFF),
    Color(0xFF2E9BFF),
    Color(0xFF16C79A),
    Color(0xFF4CD964),
    Color(0xFFFFC845),
    Color(0xFFFF8A3D),
    Color(0xFFFF5252),
    Color(0xFF9E9E9E),
)

@Composable
private fun QuickColors(current: Color, onPick: (Color) -> Unit) {
    val theme = LocalMP3roidTheme.current
    val currentHex = ThemeCatalog.hexOf(current)
    Column {
        Text(
            text = "Fertige Farben",
            style = MaterialTheme.typography.bodyMedium,
            color = theme.colors.textPrimary,
        )
        Spacer(Modifier.height(8.dp))
        QUICK_COLORS.chunked(5).forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                line.forEach { color ->
                    val chosen = ThemeCatalog.hexOf(color) == currentHex
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(color)
                            .border(
                                width = if (chosen) 2.dp else 1.dp,
                                color = if (chosen) theme.colors.textPrimary else theme.colors.hairline,
                                shape = RoundedCornerShape(13.dp),
                            )
                            .clickable { onPick(color) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ChannelSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    swatch: Color,
    onValueChange: (Float) -> Unit,
) {
    val theme = LocalMP3roidTheme.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelSmall,
                color = theme.colors.textSecondary,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = swatch,
                activeTrackColor = swatch,
                inactiveTrackColor = theme.colors.hairline,
            ),
        )
    }
}
