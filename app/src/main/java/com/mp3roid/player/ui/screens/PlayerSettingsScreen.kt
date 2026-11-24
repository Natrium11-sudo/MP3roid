package com.mp3roid.player.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsStateWithLifecycleCompat
import com.mp3roid.player.R
import com.mp3roid.player.launcher.AudioPermission
import com.mp3roid.player.launcher.NotifAccess
import com.mp3roid.player.ui.MP3roidViewModel
import com.mp3roid.player.ui.components.SleepTimerDialog
import com.mp3roid.player.ui.components.formatCountdown
import com.mp3roid.player.ui.components.formatTotalTime

/**
 * Alles, was das Abspielen betrifft. Bewusst nicht in den Einstellungen:
 * Die sind fuer den Startbildschirm da, der Player bringt seine eigenen
 * Sachen selbst mit.
 */
@Composable
fun PlayerSettingsScreen(
    vm: MP3roidViewModel,
    onBack: () -> Unit,
    onOpenEqualizer: () -> Unit,
) {
    val context = LocalContext.current
    var showSleepDialog by remember { mutableStateOf(false) }

    val sleepRemaining by vm.sleepRemainingMs.collectAsStateWithLifecycleCompat()
    val sleepMinutes by vm.sleepMinutes.collectAsStateWithLifecycleCompat()
    val effects by vm.effects.collectAsStateWithLifecycleCompat()
    val musicOnly by vm.musicOnly.collectAsStateWithLifecycleCompat()
    val hideShort by vm.hideShort.collectAsStateWithLifecycleCompat()
    val tracks by vm.tracks.collectAsStateWithLifecycleCompat()
    val scanning by vm.scanning.collectAsStateWithLifecycleCompat()
    val audioGranted by vm.audioGranted.collectAsStateWithLifecycleCompat()
    val notifGranted by vm.notifGranted.collectAsStateWithLifecycleCompat()

    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.refreshPermissions() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "Player-Einstellungen", onBack = onBack)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item {
                SectionLabel("Wiedergabe")
            }
            item {
                SettingRow(
                    iconRes = R.drawable.ic_timer,
                    title = "Sleep-Timer",
                    subtitle = if (sleepRemaining > 0L) {
                        "Läuft noch ${formatCountdown(sleepRemaining)}"
                    } else if (sleepMinutes > 0) {
                        "$sleepMinutes Minuten"
                    } else {
                        "Aus"
                    },
                    onClick = { showSleepDialog = true },
                )
            }
            item {
                SettingRow(
                    iconRes = R.drawable.ic_equalizer,
                    title = "Equalizer",
                    subtitle = if (effects.available) {
                        if (effects.enabled) "Aktiv" else "Inaktiv"
                    } else {
                        "Wird beim Abspielen verfügbar"
                    },
                    onClick = onOpenEqualizer,
                )
            }

            item {
                SectionLabel("Bibliothek")
            }
            item {
                SwitchRow(
                    iconRes = R.drawable.ic_library_music,
                    title = "Nur als Musik markierte Dateien",
                    subtitle = "Blendet Klingeltöne und Sprachaufnahmen aus",
                    checked = musicOnly,
                    onCheckedChange = vm::setMusicOnly,
                )
            }
            item {
                SwitchRow(
                    iconRes = R.drawable.ic_speed,
                    title = "Sehr kurze Dateien ausblenden",
                    subtitle = "Alles unter 20 Sekunden",
                    checked = hideShort,
                    onCheckedChange = vm::setHideShort,
                )
            }
            item {
                SettingRow(
                    iconRes = R.drawable.ic_refresh,
                    title = "Bibliothek neu einlesen",
                    subtitle = if (scanning) {
                        "Läuft …"
                    } else {
                        "${tracks.size} Titel · ${formatTotalTime(tracks.sumOf { it.durationMs })}"
                    },
                    onClick = vm::refreshLibrary,
                )
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

            item {
                SectionLabel("Fremde Wiedergabe")
            }
            item {
                SettingRow(
                    iconRes = R.drawable.ic_notifications,
                    title = "Wiedergabe-Infos fremder Apps",
                    subtitle = if (notifGranted) {
                        "Aktiv – Spotify und Co. werden angezeigt"
                    } else {
                        "Inaktiv – dazu Benachrichtigungszugriff erlauben"
                    },
                    highlighted = !notifGranted,
                    onClick = {
                        runCatching { context.startActivity(NotifAccess.requestIntent(context)) }
                    },
                )
            }
        }
    }

    if (showSleepDialog) {
        SleepTimerDialog(
            currentMinutes = sleepMinutes,
            remainingMs = sleepRemaining,
            onSelect = { minutes ->
                if (minutes == 0) vm.cancelSleepTimer() else vm.setSleepTimer(minutes)
            },
            onDismiss = { showSleepDialog = false },
        )
    }
}
