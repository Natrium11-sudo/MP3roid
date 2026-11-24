package com.mp3roid.player.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mp3roid.player.R
import com.mp3roid.player.data.Album
import com.mp3roid.player.data.ArtworkLoader
import com.mp3roid.player.data.Playlist
import com.mp3roid.player.data.Track
import com.mp3roid.player.ui.theme.LocalMP3roidTheme

@Composable
fun TrackActionsDialog(
    track: Track,
    playlists: List<Playlist>,
    favorite: Boolean,
    hasLocalQueue: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onCreatePlaylist: () -> Unit,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val theme = LocalMP3roidTheme.current
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showNewPlaylist by remember { mutableStateOf(false) }

    if (showNewPlaylist) {
        NewPlaylistDialog(
            onCreate = { name ->
                onAddToPlaylist(name)
                showNewPlaylist = false
                onDismiss()
            },
            onDismiss = { showNewPlaylist = false },
        )
        return
    }

    if (showPlaylistPicker) {
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            title = { Text("Zu Playlist hinzufügen") },
            text = {
                Column {
                    if (playlists.isEmpty()) {
                        Text(
                            text = "Noch keine Playlist vorhanden.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.colors.textSecondary,
                        )
                    }
                    playlists.forEach { playlist ->
                        DialogRow(
                            iconRes = R.drawable.ic_playlist_play,
                            label = "${playlist.name} (${playlist.trackIds.size})",
                            onClick = {
                                onAddToPlaylist(playlist.name)
                                showPlaylistPicker = false
                                onDismiss()
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNewPlaylist = true }) { Text("Neue Playlist") }
            },
            dismissButton = {
                TextButton(onClick = { showPlaylistPicker = false }) { Text("Zurück") }
            },
            containerColor = theme.colors.surfaceElevated,
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist.ifBlank { "Unbekannter Interpret" },
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        text = {
            Column {
                if (hasLocalQueue) {
                    DialogRow(
                        iconRes = R.drawable.ic_playlist_play,
                        label = "Als Nächstes abspielen",
                        onClick = {
                            onPlayNext()
                            onDismiss()
                        },
                    )
                    DialogRow(
                        iconRes = R.drawable.ic_queue_music,
                        label = "Zur Warteschlange",
                        onClick = {
                            onAddToQueue()
                            onDismiss()
                        },
                    )
                }
                DialogRow(
                    iconRes = R.drawable.ic_playlist_play,
                    label = "Zu Playlist hinzufügen",
                    onClick = { showPlaylistPicker = true },
                )
                DialogRow(
                    iconRes = if (favorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
                    label = if (favorite) "Aus Favoriten entfernen" else "Zu Favoriten",
                    onClick = {
                        onToggleFavorite()
                        onDismiss()
                    },
                )
                if (onRename != null) {
                    // Schließt nicht das Menü, sondern ersetzt es durch den
                    // Bearbeiten-Dialog (siehe TrackActionHost).
                    DialogRow(
                        iconRes = R.drawable.ic_edit,
                        label = "Titel bearbeiten",
                        onClick = onRename,
                    )
                }
                if (onDelete != null) {
                    DialogRow(
                        iconRes = R.drawable.ic_delete,
                        label = "Löschen",
                        onClick = onDelete,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        },
        containerColor = theme.colors.surfaceElevated,
    )
}

@Composable
fun NewPlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    val theme = LocalMP3roidTheme.current
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neue Playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Name") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank(),
            ) { Text("Anlegen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
        containerColor = theme.colors.surfaceElevated,
    )
}

/**
 * Titel eines einzelnen Eintrags ändern – erreichbar über den langen Druck
 * auf eine Titelzeile. Die Änderung gilt nur in MP3roid; die Datei selbst
 * bleibt unangetastet, das behält Android ohnehin vor.
 */
@Composable
fun EditTrackTitleDialog(
    track: Track,
    hasTitleOverride: Boolean,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalMP3roidTheme.current
    var title by remember(track.id) { mutableStateOf(track.title) }
    var overridden by remember(track.id) { mutableStateOf(hasTitleOverride) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Titel bearbeiten") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text("Titel") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (overridden) {
                    DialogRow(iconRes = R.drawable.ic_refresh, label = "Originalen Titel wiederherstellen") {
                        overridden = false
                        onReset()
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Gilt nur in MP3roid – die Datei bleibt unverändert.",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.colors.textSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(title.trim())
                    onDismiss()
                },
                enabled = title.isNotBlank(),
            ) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
        containerColor = theme.colors.surfaceElevated,
    )
}

/**
 * Album umbenennen und sein Cover wählen – erreichbar über den langen Druck
 * auf eine Albumkachel bzw. das Cover in der Albumansicht.
 *
 * Das Bild wird als verkleinertes JPEG im privaten Speicher abgelegt und
 * schlägt jede andere Cover-Quelle; der Hinweis unten sagt dem Nutzer, dass
 * die Änderung in MP3roid gilt und die Dateien unverändert bleiben.
 */
@Composable
fun EditAlbumDialog(
    album: Album,
    artwork: ArtworkLoader,
    hasNameOverride: Boolean,
    hasCustomCover: Boolean,
    onSaveName: (String) -> Unit,
    onResetName: () -> Unit,
    onPickCover: (Uri) -> Unit,
    onClearCover: () -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalMP3roidTheme.current
    var name by remember(album.id) { mutableStateOf(album.name) }
    var overridden by remember(album.id) { mutableStateOf(hasNameOverride) }
    var coverChosen by remember(album.id) { mutableStateOf(hasCustomCover) }

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            coverChosen = true
            onPickCover(uri)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Album bearbeiten") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoverArt(
                        loader = artwork,
                        albumId = album.id,
                        seed = name,
                        corner = theme.colors.cardCorner,
                        iconSize = 26.dp,
                        modifier = Modifier.size(92.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        DialogRow(iconRes = R.drawable.ic_image, label = "Cover wählen") {
                            coverPicker.launch("image/*")
                        }
                        if (coverChosen) {
                            DialogRow(iconRes = R.drawable.ic_delete, label = "Cover entfernen") {
                                coverChosen = false
                                onClearCover()
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Albumname") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (overridden) {
                    DialogRow(iconRes = R.drawable.ic_refresh, label = "Originalen Namen wiederherstellen") {
                        overridden = false
                        onResetName()
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Gilt nur in MP3roid – die Dateien bleiben unverändert.",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.colors.textSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSaveName(name.trim())
                    onDismiss()
                },
                enabled = name.isNotBlank(),
            ) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
        containerColor = theme.colors.surfaceElevated,
    )
}

/**
 * Menü zu einer Theme-Kachel: bearbeiten, duplizieren, löschen.
 *
 * Die Rückfrage vor dem Löschen steckt hier drin, damit ein eigener Dialog
 * genügt.
 */
@Composable
fun ThemeActionsDialog(
    themeName: String,
    isOwn: Boolean,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalMP3roidTheme.current
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Theme löschen?") },
            text = {
                Text(
                    "„$themeName\u201C wird entfernt. Danach gilt wieder „Dark Void\u201C. " +
                        "Die mitgelieferten Themes bleiben unberührt.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") }
            },
            containerColor = theme.colors.surfaceElevated,
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(themeName) },
        text = {
            Column {
                if (isOwn) {
                    DialogRow(R.drawable.ic_tune, "Bearbeiten") {
                        onEdit()
                        onDismiss()
                    }
                }
                DialogRow(R.drawable.ic_auto_awesome, "Duplizieren und bearbeiten") {
                    onDuplicate()
                    onDismiss()
                }
                if (isOwn) {
                    DialogRow(R.drawable.ic_delete, "Löschen") { confirmDelete = true }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        },
        containerColor = theme.colors.surfaceElevated,
    )
}

@Composable
private fun DialogRow(iconRes: Int, label: String, onClick: () -> Unit) {
    val theme = LocalMP3roidTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
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

@Composable
fun SleepTimerDialog(
    currentMinutes: Int,
    remainingMs: Long,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalMP3roidTheme.current
    val options = listOf(0, 5, 10, 15, 30, 45, 60, 90)
    // Eigene Zeiten stehen nicht in der Liste - dann das Feld damit vorbelegen,
    // damit man sie beim naechsten Mal gleich wieder sieht.
    var custom by remember {
        mutableStateOf(
            if (currentMinutes > 0 && currentMinutes !in options) {
                currentMinutes.toString()
            } else {
                ""
            },
        )
    }
    val customMinutes = custom.toIntOrNull()?.takeIf { it in 1..999 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep-Timer") },
        text = {
            Column {
                if (remainingMs > 0L) {
                    Text(
                        text = "Läuft noch ${formatCountdown(remainingMs)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.colors.accent,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    options.forEach { minutes ->
                        SelectChip(
                            label = if (minutes == 0) "Aus" else "$minutes Min",
                            selected = minutes == currentMinutes,
                            onClick = {
                                onSelect(minutes)
                                onDismiss()
                            },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = custom,
                    onValueChange = { raw -> custom = raw.filter { it.isDigit() }.take(3) },
                    singleLine = true,
                    label = { Text("Eigene Minuten") },
                    placeholder = { Text("z. B. 25") },
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    customMinutes?.let(onSelect)
                    onDismiss()
                },
                enabled = customMinutes != null,
            ) { Text("Starten") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        },
        containerColor = theme.colors.surfaceElevated,
    )
}
