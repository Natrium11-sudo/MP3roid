package com.mp3roid.player.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsStateWithLifecycleCompat
import com.mp3roid.player.R
import com.mp3roid.player.launcher.AudioPermission
import com.mp3roid.player.launcher.HomeRole
import com.mp3roid.player.launcher.NotifAccess
import com.mp3roid.player.ui.MP3roidViewModel
import com.mp3roid.player.ui.components.MP3roidIconButton
import com.mp3roid.player.ui.theme.LocalMP3roidTheme

@Composable
fun OnboardingScreen(vm: MP3roidViewModel, onDone: () -> Unit) {
    val theme = LocalMP3roidTheme.current
    val context = LocalContext.current

    val homeHeld by vm.homeRoleHeld.collectAsStateWithLifecycleCompat()
    val audioGranted by vm.audioGranted.collectAsStateWithLifecycleCompat()
    val notifGranted by vm.notifGranted.collectAsStateWithLifecycleCompat()

    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.refreshPermissions() }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { vm.refreshPermissions() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(theme.colors.accent),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "MP3roid",
                style = MaterialTheme.typography.headlineMedium,
                color = theme.colors.textPrimary,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Dein Handy wird zum Musikgerät: der Startbildschirm ist der Player.",
            style = MaterialTheme.typography.bodyLarge,
            color = theme.colors.textSecondary,
        )

        Spacer(Modifier.height(28.dp))

        StepCard(
            index = 1,
            title = "Startbildschirm",
            text = "MP3roid ersetzt den normalen Startbildschirm. Beim Drücken der Home-Taste " +
                "landest du immer direkt beim Player.",
            done = homeHeld,
            actionLabel = "Erlauben",
            onAction = {
                val intent = HomeRole.requestIntent(context)
                if (intent != null) {
                    roleLauncher.launch(intent)
                } else {
                    HomeRole.openDefaultAppsSettings(context)
                }
            },
        )

        Spacer(Modifier.height(14.dp))

        StepCard(
            index = 2,
            title = "Musik lesen",
            text = "Damit MP3roid deine MP3s findet, braucht es Zugriff auf die Audiodateien " +
                "auf dem Gerät und der Speicherkarte.",
            done = audioGranted,
            actionLabel = "Erlauben",
            onAction = { audioLauncher.launch(AudioPermission.required()) },
        )

        Spacer(Modifier.height(14.dp))

        StepCard(
            index = 3,
            title = "Fremde Player sehen",
            text = "Spotify, YouTube-Music-Clients und VLC melden ihre Wiedergabe nur über " +
                "den Benachrichtigungszugriff. Ohne dieses Recht bleiben sie unsichtbar.",
            done = notifGranted,
            actionLabel = "Erlauben",
            onAction = {
                runCatching { context.startActivity(NotifAccess.requestIntent(context)) }
            },
        )

        Spacer(Modifier.height(26.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDone) {
                Text("Später einrichten", color = theme.colors.textSecondary)
            }
            Spacer(Modifier.width(8.dp))
            MP3roidIconButton(
                iconRes = R.drawable.ic_check,
                contentDescription = "Fertig",
                onClick = onDone,
                filled = true,
                size = 52.dp,
                iconSize = 24.dp,
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "Alle drei Punkte lassen sich später in den Einstellungen nachholen – " +
                "zu erreichen über das Zahnrad oben rechts.",
            style = MaterialTheme.typography.labelSmall,
            color = theme.colors.textSecondary,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StepCard(
    index: Int,
    title: String,
    text: String,
    done: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val theme = LocalMP3roidTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(theme.colors.surface)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        if (done) theme.colors.accent else theme.colors.surfaceElevated,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = theme.colors.onAccent,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text(
                        text = "$index",
                        style = MaterialTheme.typography.labelLarge,
                        color = theme.colors.textPrimary,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = theme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (done) {
                Text(
                    text = "Erledigt",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.colors.accent,
                )
            } else {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = theme.colors.textSecondary,
        )
    }
}
