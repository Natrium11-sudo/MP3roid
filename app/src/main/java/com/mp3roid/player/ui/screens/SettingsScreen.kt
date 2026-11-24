package com.mp3roid.player.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsStateWithLifecycleCompat
import com.mp3roid.player.R
import com.mp3roid.player.launcher.AudioPermission
import com.mp3roid.player.launcher.HomeRole
import com.mp3roid.player.launcher.NotifAccess
import com.mp3roid.player.ui.MP3roidViewModel
import com.mp3roid.player.ui.WallpaperState
import com.mp3roid.player.ui.components.MP3roidIconButton
import com.mp3roid.player.ui.components.SelectChip
import com.mp3roid.player.ui.components.ThemeActionsDialog
import com.mp3roid.player.ui.theme.MP3roidTheme
import com.mp3roid.player.ui.theme.LocalMP3roidTheme
import com.mp3roid.player.ui.theme.ThemeCatalog
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    vm: MP3roidViewModel,
    onBack: () -> Unit,
    onOpenThemeEditor: () -> Unit,
) {
    val theme = LocalMP3roidTheme.current
    val context = LocalContext.current

    val themes by vm.themes.collectAsStateWithLifecycleCompat()
    var actionTheme by remember { mutableStateOf<MP3roidTheme?>(null) }

    val showBars by vm.showSystemBars.collectAsStateWithLifecycleCompat()
    val homeHeld by vm.homeRoleHeld.collectAsStateWithLifecycleCompat()
    val notifGranted by vm.notifGranted.collectAsStateWithLifecycleCompat()
    val audioGranted by vm.audioGranted.collectAsStateWithLifecycleCompat()

    val useWallpaper by vm.useWallpaper.collectAsStateWithLifecycleCompat()
    val wallpaperDim by vm.wallpaperDim.collectAsStateWithLifecycleCompat()
    val wallpaperSoft by vm.wallpaperSoft.collectAsStateWithLifecycleCompat()
    val wallpaper by vm.wallpaper.collectAsStateWithLifecycleCompat()

    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.refreshPermissions() }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { vm.refreshPermissions() }

    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "Einstellungen", onBack = onBack)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
        ) {
            // Nur sichtbar, solange etwas fehlt. Danach ist diese Seite reine
            // Startbildschirm-Einstellung.
            if (!audioGranted || !notifGranted) {
                item {
                    SectionLabel("Einrichtung")
                }
                if (!audioGranted) {
                    item {
                        SettingRow(
                            iconRes = R.drawable.ic_music_note,
                            title = "Zugriff auf Audiodateien erlauben",
                            subtitle = "Wird benötigt, um deine MP3s zu finden",
                            highlighted = true,
                            onClick = { audioLauncher.launch(AudioPermission.required()) },
                        )
                    }
                }
                if (!notifGranted) {
                    item {
                        SettingRow(
                            iconRes = R.drawable.ic_notifications,
                            title = "Wiedergabe-Infos fremder Apps",
                            subtitle = "Inaktiv – dazu Benachrichtigungszugriff erlauben",
                            highlighted = true,
                            onClick = {
                                runCatching { context.startActivity(NotifAccess.requestIntent(context)) }
                            },
                        )
                    }
                }
            }

            item {
                SectionLabel("Darstellung")
            }
            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(themes, key = { it.id }) { entry ->
                        ThemeCard(
                            theme = entry,
                            selected = entry.id == theme.id,
                            own = ThemeCatalog.isOwn(entry.id),
                            onClick = { vm.setTheme(entry.id) },
                            onEdit = {
                                vm.setTheme(entry.id)
                                onOpenThemeEditor()
                            },
                            onLongClick = { actionTheme = entry },
                        )
                    }
                    item(key = "add") {
                        AddThemeCard(
                            onClick = {
                                vm.createTheme()?.let { onOpenThemeEditor() }
                            },
                        )
                    }
                }
            }
            item {
                Text(
                    text = "„Eigenes\u201C legt eine Kopie der aktuellen Optik an – Farben und Form " +
                        "stellt man danach direkt im Editor ein. Langer Druck auf eine Kachel: " +
                        "Bearbeiten, Duplizieren, Löschen.",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.colors.textSecondary.copy(alpha = 0.75f),
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp),
                )
            }

            item {
                SectionLabel("Hintergrundbild")
            }
            item {
                SwitchRow(
                    iconRes = R.drawable.ic_wallpaper,
                    title = "Hintergrundbild vom Handy",
                    subtitle = if (useWallpaper) {
                        "MP3roid übernimmt dein System-Hintergrundbild"
                    } else {
                        "Stattdessen der Verlauf des Themes"
                    },
                    checked = useWallpaper,
                    onCheckedChange = vm::setUseWallpaper,
                )
            }
            if (useWallpaper) {
                // Klappt das Lesen nicht (keine Tapete, Animation, gesperrtes
                // Bild), bleibt es beim Verlauf – hier steht dann der Grund.
                val wallpaperReason = (wallpaper as? WallpaperState.Unavailable)?.reason
                if (wallpaperReason != null) {
                    item {
                        HintText(wallpaperReason)
                    }
                }
                item {
                    SliderRow(
                        label = "Abdunkeln",
                        value = wallpaperDim,
                        range = 0..80,
                        suffix = "%",
                        onChange = vm::setWallpaperDim,
                    )
                }
                item {
                    SwitchRow(
                        iconRes = R.drawable.ic_blur,
                        title = "Weichzeichnen",
                        subtitle = "Macht das Bild ruhiger, damit Schrift gut lesbar bleibt",
                        checked = wallpaperSoft,
                        onCheckedChange = vm::setWallpaperSoft,
                    )
                }
            }

            item {
                SectionLabel("Startbildschirm")
            }
            item {
                SettingRow(
                    iconRes = R.drawable.ic_home,
                    title = "Startbildschirm",
                    subtitle = if (homeHeld) {
                        "MP3roid ist der Startbildschirm"
                    } else {
                        "Noch nicht eingerichtet – antippen"
                    },
                    highlighted = !homeHeld,
                    onClick = {
                        val intent = HomeRole.requestIntent(context)
                        if (intent != null) {
                            roleLauncher.launch(intent)
                        } else {
                            HomeRole.openDefaultAppsSettings(context)
                        }
                    },
                )
            }
            item {
                SwitchRow(
                    iconRes = R.drawable.ic_grid_view,
                    title = "Systemleisten anzeigen",
                    subtitle = "Standardmäßig blendet MP3roid sie aus",
                    checked = showBars,
                    onCheckedChange = vm::setShowSystemBars,
                )
            }

            item {
                SectionLabel("Über")
            }
            item {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                    Text(
                        text = "MP3roid",
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.colors.textPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Ein Startbildschirm, der gleichzeitig ein Musikplayer ist. " +
                            "Lokale Dateien und fremde Player – Spotify, YouTube-Music-Clients, VLC – " +
                            "landen auf einer Seite. Themes liegen als JSON in assets/themes/ und " +
                            "lassen sich ohne Code ergänzen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.colors.textSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Version $version",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.colors.textSecondary,
                    )
                }
            }
        }
    }

    actionTheme?.let { entry ->
        ThemeActionsDialog(
            themeName = entry.name,
            isOwn = ThemeCatalog.isOwn(entry.id),
            onEdit = {
                actionTheme = null
                vm.setTheme(entry.id)
                onOpenThemeEditor()
            },
            onDuplicate = {
                actionTheme = null
                vm.duplicateTheme(entry.id)?.let { onOpenThemeEditor() }
            },
            onDelete = {
                actionTheme = null
                vm.deleteOwnTheme(entry.id)
            },
            onDismiss = { actionTheme = null },
        )
    }
}

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    val theme = LocalMP3roidTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MP3roidIconButton(
            iconRes = R.drawable.ic_arrow_back,
            contentDescription = "Zurück",
            onClick = onBack,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = theme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun SectionLabel(text: String) {
    val theme = LocalMP3roidTheme.current
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = theme.colors.accent,
        modifier = Modifier.padding(start = 18.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
fun SettingRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    val theme = LocalMP3roidTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    if (highlighted) {
                        theme.colors.accent.copy(alpha = 0.22f)
                    } else {
                        theme.colors.surfaceElevated
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = if (highlighted) theme.colors.accent else theme.colors.textSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = theme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (highlighted) theme.colors.accent else theme.colors.textSecondary,
            )
        }
    }
}

@Composable
fun SwitchRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val theme = LocalMP3roidTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(theme.colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = theme.colors.textSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = theme.colors.textPrimary,
                maxLines = 2,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = theme.colors.textSecondary,
                maxLines = 2,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Kleine Erklärung unter einem Schalter. */
@Composable
private fun HintText(text: String) {
    val theme = LocalMP3roidTheme.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = theme.colors.textSecondary,
        modifier = Modifier.padding(start = 68.dp, end = 18.dp, top = 2.dp, bottom = 6.dp),
    )
}

/**
 * Regler mit Wert daneben. Bewusst ohne Rasterschritte: die vielen kleinen
 * Striche eines gerasterten Reglers würden hier nur unruhig wirken.
 */
@Composable
private fun SliderRow(
    label: String,
    value: Int,
    range: IntRange,
    suffix: String,
    onChange: (Int) -> Unit,
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
                text = "$value$suffix",
                style = MaterialTheme.typography.labelMedium,
                color = theme.colors.textSecondary,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = theme.colors.accent,
                activeTrackColor = theme.colors.accent,
                inactiveTrackColor = theme.colors.hairline,
            ),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThemeCard(
    theme: MP3roidTheme,
    selected: Boolean,
    own: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onLongClick: () -> Unit,
) {
    val current = LocalMP3roidTheme.current
    Column(
        modifier = Modifier
            .width(108.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(theme.colors.background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            theme.colors.gradientTop,
                            theme.colors.gradientBottom,
                        ),
                    ),
                ),
        ) {
            if (own) {
                // Direkter Weg in den Editor; der lange Druck öffnet das Menü.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(current.colors.surface.copy(alpha = 0.85f))
                        .clickable(onClick = onEdit),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_tune),
                        contentDescription = "Theme bearbeiten",
                        tint = current.colors.accent,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = theme.name,
            style = MaterialTheme.typography.labelMedium,
            color = theme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(theme.colors.accent),
            )
            Spacer(Modifier.width(5.dp))
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(theme.colors.accentSecondary),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (selected) "✓" else if (own) "Eigenes" else "",
                color = if (selected) current.colors.accent else current.colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

/** Letzte Kachel der Theme-Reihe: legt eine eigene Kopie der aktuellen Optik an. */
@Composable
private fun AddThemeCard(onClick: () -> Unit) {
    val current = LocalMP3roidTheme.current
    Column(
        modifier = Modifier
            .width(108.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(current.colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(current.colors.background),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = null,
                tint = current.colors.accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Eigenes",
            style = MaterialTheme.typography.labelMedium,
            color = current.colors.textPrimary,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "anlegen",
            style = MaterialTheme.typography.labelSmall,
            color = current.colors.textSecondary,
            maxLines = 1,
        )
    }
}
