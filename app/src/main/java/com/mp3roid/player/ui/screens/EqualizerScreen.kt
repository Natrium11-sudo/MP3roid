package com.mp3roid.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsStateWithLifecycleCompat
import com.mp3roid.player.ui.MP3roidViewModel
import com.mp3roid.player.ui.components.SelectChip
import com.mp3roid.player.ui.theme.LocalMP3roidTheme

@Composable
fun EqualizerScreen(vm: MP3roidViewModel, onBack: () -> Unit) {
    val theme = LocalMP3roidTheme.current
    val effects by vm.effects.collectAsStateWithLifecycleCompat()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "Equalizer", onBack = onBack)

        if (!effects.available) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    text = "Noch nicht verfügbar",
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.colors.textPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Equalizer und Bass-Boost hängen an der Audio-Ausgabe. " +
                        "Spiele einmal etwas ab – danach lässt sich hier feinjustieren. " +
                        "Manche Geräte bieten diese Effekte gar nicht an; dann bleibt " +
                        "diese Seite leer und die Wiedergabe läuft unverändert.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.colors.textSecondary,
                )
            }
            return
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Equalizer aktiv",
                            style = MaterialTheme.typography.bodyLarge,
                            color = theme.colors.textPrimary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Wirkt nur auf die eigene Wiedergabe",
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.colors.textSecondary,
                        )
                    }
                    Switch(
                        checked = effects.enabled,
                        onCheckedChange = vm::setEqualizerEnabled,
                    )
                }
            }

            if (effects.presets.isNotEmpty()) {
                item {
                    SectionLabel("Voreinstellungen")
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(effects.presets.withIndex().toList()) { entry ->
                            SelectChip(
                                label = entry.value,
                                selected = entry.index == effects.preset,
                                onClick = { vm.setEqualizerPreset(entry.index) },
                            )
                        }
                    }
                }
            }

            item {
                SectionLabel("Bänder")
            }
            items(effects.bands) { band ->
                Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = bandLabel(band.frequencyHz),
                            style = MaterialTheme.typography.labelMedium,
                            color = theme.colors.textSecondary,
                            modifier = Modifier.width(56.dp),
                        )
                        Slider(
                            value = band.levelMb.toFloat(),
                            onValueChange = { vm.setEqualizerBand(band.index, it.toInt()) },
                            valueRange = band.minMb.toFloat()..band.maxMb.toFloat(),
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = theme.colors.accent,
                                activeTrackColor = theme.colors.accent,
                                inactiveTrackColor = theme.colors.hairline,
                            ),
                        )
                        Text(
                            text = "%+.1f dB".format(band.levelMb / 100f),
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.width(60.dp),
                        )
                    }
                }
            }

            if (effects.bassAvailable) {
                item {
                    SectionLabel("Bass-Boost")
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Bass",
                            style = MaterialTheme.typography.labelMedium,
                            color = theme.colors.textSecondary,
                            modifier = Modifier.width(56.dp),
                        )
                        Slider(
                            value = effects.bassStrength.toFloat(),
                            onValueChange = { vm.setBassStrength(it.toInt()) },
                            valueRange = 0f..effects.bassMax.toFloat(),
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = theme.colors.accent,
                                activeTrackColor = theme.colors.accent,
                                inactiveTrackColor = theme.colors.hairline,
                            ),
                        )
                        Text(
                            text = "${(effects.bassStrength * 100 / effects.bassMax.coerceAtLeast(1))}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.colors.textSecondary,
                            modifier = Modifier.width(60.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun bandLabel(hertz: Int): String = when {
    hertz <= 0 -> "—"
    hertz >= 10_000 -> "%.0fk".format(hertz / 1000f)
    hertz >= 1_000 -> "%.1fk".format(hertz / 1000f)
    else -> "$hertz"
}
