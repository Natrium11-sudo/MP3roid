package com.mp3roid.player.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsStateWithLifecycleCompat
import com.mp3roid.player.R
import com.mp3roid.player.data.AppEntry
import com.mp3roid.player.ui.MP3roidViewModel
import com.mp3roid.player.ui.components.EmptyState
import com.mp3roid.player.ui.components.MP3roidIconButton
import com.mp3roid.player.ui.components.initialsOf
import com.mp3roid.player.ui.theme.LocalMP3roidTheme

@Composable
fun AppsPage(
    vm: MP3roidViewModel,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalMP3roidTheme.current
    val apps by vm.apps.collectAsStateWithLifecycleCompat()
    val loading by vm.appsLoading.collectAsStateWithLifecycleCompat()
    var query by remember { mutableStateOf("") }
    var actionApp by remember { mutableStateOf<AppEntry?>(null) }

    val needle = query.trim().lowercase()
    val visible = remember(needle, apps) {
        if (needle.isEmpty()) apps else apps.filter { it.label.lowercase().contains(needle) }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${apps.size} Apps",
                style = MaterialTheme.typography.labelSmall,
                color = theme.colors.textSecondary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                // Der Hinweis auf den langen Druck steht sonst nur im Leerzustand,
                // den man nie zu sehen bekommt.
                text = "langer Druck: App-Info & Deinstallieren",
                style = MaterialTheme.typography.labelSmall,
                color = theme.colors.textSecondary.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            MP3roidIconButton(
                iconRes = R.drawable.ic_refresh,
                contentDescription = "App-Liste erneuern",
                onClick = vm::refreshApps,
                size = 40.dp,
                iconSize = 20.dp,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(theme.colors.surfaceElevated)
                .clickable(onClick = onOpenSearch)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = theme.colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Apps und Titel durchsuchen",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.colors.textSecondary,
            )
        }

        if (loading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = theme.colors.accent,
                trackColor = theme.colors.hairline,
            )
            Spacer(Modifier.height(2.dp))
        }

        if (visible.isEmpty()) {
            EmptyState(
                iconRes = R.drawable.ic_apps,
                title = if (needle.isEmpty()) "Keine Apps gefunden" else "Nichts gefunden",
                hint = "Hier erscheinen alle installierten Apps. Tippen öffnet sie, " +
                    "langer Druck zeigt App-Info und Deinstallieren.",
            )
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(visible, key = { it.packageName }) { app ->
                AppCell(
                    app = app,
                    onClick = { vm.launchApp(app) },
                    onLongClick = { actionApp = app },
                )
            }
        }
    }

    actionApp?.let { app ->
        AlertDialog(
            onDismissRequest = { actionApp = null },
            title = { Text(app.label) },
            text = {
                Column {
                    AppDialogRow(
                        iconRes = R.drawable.ic_play_arrow,
                        label = "Öffnen",
                        onClick = {
                            vm.launchApp(app)
                            actionApp = null
                        },
                    )
                    AppDialogRow(
                        iconRes = R.drawable.ic_info,
                        label = "App-Info",
                        onClick = {
                            vm.appInfo(app)
                            actionApp = null
                        },
                    )
                    AppDialogRow(
                        iconRes = R.drawable.ic_delete,
                        label = "Deinstallieren",
                        onClick = {
                            vm.uninstallApp(app)
                            actionApp = null
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { actionApp = null }) { Text("Schließen") }
            },
            containerColor = theme.colors.surfaceElevated,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCell(app: AppEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    val theme = LocalMP3roidTheme.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(app = app, size = 52.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall,
            color = theme.colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun AppIcon(app: AppEntry, size: Dp) {
    val theme = LocalMP3roidTheme.current
    val shape = RoundedCornerShape(size / 4.6f)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(theme.colors.surfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        val icon = app.icon
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = initialsOf(app.label),
                style = MaterialTheme.typography.titleMedium,
                color = theme.colors.accent,
            )
        }
    }
}

@Composable
private fun AppDialogRow(iconRes: Int, label: String, onClick: () -> Unit) {
    val theme = LocalMP3roidTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = theme.colors.accent,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = theme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
