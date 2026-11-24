package com.mp3roid.player.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsStateWithLifecycleCompat
import com.mp3roid.player.R
import com.mp3roid.player.data.ArtworkLoader
import com.mp3roid.player.playback.NowPlaying
import com.mp3roid.player.playback.QueueItem
import com.mp3roid.player.playback.SourceOption
import com.mp3roid.player.ui.MP3roidViewModel
import com.mp3roid.player.ui.components.CoverArt
import com.mp3roid.player.ui.components.EmptyState
import com.mp3roid.player.ui.components.MP3roidIconButton
import com.mp3roid.player.ui.components.SectionHeader
import com.mp3roid.player.ui.components.SleepTimerDialog
import com.mp3roid.player.ui.components.VolumeState
import com.mp3roid.player.ui.components.formatCountdown
import com.mp3roid.player.ui.components.formatTime
import com.mp3roid.player.ui.components.rememberVolumeState
import com.mp3roid.player.ui.theme.LocalMP3roidTheme

@Composable
fun PlayerPage(
    vm: MP3roidViewModel,
    now: NowPlaying?,
    sources: List<SourceOption>,
    upNext: List<QueueItem>,
    onOpenLibrary: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenPlayerSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalMP3roidTheme.current

    if (now == null) {
        Column(modifier.fillMaxSize()) {
            EmptyState(
                iconRes = R.drawable.ic_headphones,
                title = "Nichts läuft gerade",
                hint = "Wähle in der Bibliothek etwas aus – oder starte Spotify. " +
                    "Was dort läuft, erscheint hier automatisch.",
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                MP3roidIconButton(
                    iconRes = R.drawable.ic_library_music,
                    contentDescription = "Bibliothek öffnen",
                    onClick = onOpenLibrary,
                    filled = true,
                    size = 54.dp,
                    iconSize = 24.dp,
                )
            }
        }
        return
    }

    val loader = vm.artwork

    val favorites by vm.favorites.collectAsStateWithLifecycleCompat()
    val sleepRemaining by vm.sleepRemainingMs.collectAsStateWithLifecycleCompat()
    val sleepMinutes by vm.sleepMinutes.collectAsStateWithLifecycleCompat()
    val effects by vm.effects.collectAsStateWithLifecycleCompat()
    var showSleepDialog by remember { mutableStateOf(false) }
    val volume = rememberVolumeState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (sources.size > 1) {
            Spacer(Modifier.height(6.dp))
            SourceRow(sources = sources, activeId = now.sourceId, onSelect = vm::selectSource)
        }

        Spacer(Modifier.height(10.dp))

        SwipeableCover(
            now = now,
            loader = loader,
            sources = sources,
            onPreviousSource = { shiftSource(sources, now.sourceId, -1, vm::selectSource) },
            onNextSource = { shiftSource(sources, now.sourceId, 1, vm::selectSource) },
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = now.title,
            style = MaterialTheme.typography.headlineSmall,
            color = theme.colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = now.artist.ifBlank { "Unbekannter Interpret" },
            style = MaterialTheme.typography.titleMedium,
            color = theme.colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!now.isLocal) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Wiedergabe von ${now.sourceLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = theme.colors.accent,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(14.dp))

        SeekRow(now = now, onSeek = vm::seekTo)

        Spacer(Modifier.height(2.dp))

        TransportRow(now = now, vm = vm)

        Spacer(Modifier.height(16.dp))

        ToolRow(
            now = now,
            favorites = favorites,
            sleepRemainingMs = sleepRemaining,
            sleepMinutes = sleepMinutes,
            effectsAvailable = effects.available,
            onToggleFavorite = { if (now.trackId > 0L) vm.toggleFavorite(now.trackId) },
            onOpenSleepTimer = { showSleepDialog = true },
            onOpenQueue = onOpenQueue,
            onOpenEqualizer = onOpenEqualizer,
        )

        if (sleepRemaining > 0L) {
            Spacer(Modifier.height(12.dp))
            SleepTimerPill(
                remainingMs = sleepRemaining,
                onClick = { showSleepDialog = true },
            )
        }

        Spacer(Modifier.height(10.dp))

        SecondaryRow(volume = volume)

        if (upNext.isNotEmpty()) {
            Column(Modifier.fillMaxWidth()) {
                SectionHeader(title = "Als Nächstes")
                upNext.take(5).forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_drag_handle),
                            contentDescription = null,
                            tint = theme.colors.textSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (item.artist.isNotBlank()) {
                                Text(
                                    text = item.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = theme.colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onOpenPlayerSettings)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = null,
                tint = theme.colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Player-Einstellungen",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.colors.textSecondary,
            )
        }

        Spacer(Modifier.height(28.dp))
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

private fun shiftSource(
    sources: List<SourceOption>,
    activeId: String,
    delta: Int,
    select: (String) -> Unit,
) {
    if (sources.size < 2) return
    val index = sources.indexOfFirst { it.id == activeId }.let { if (it < 0) 0 else it }
    val next = (index + delta + sources.size) % sources.size
    select(sources[next].id)
}

@Composable
private fun SwipeableCover(
    now: NowPlaying,
    loader: ArtworkLoader,
    sources: List<SourceOption>,
    onPreviousSource: () -> Unit,
    onNextSource: () -> Unit,
) {
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    val swipe = if (sources.size > 1) {
        Modifier.pointerInput(now.sourceId, sources.size) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (dragAccumulator > SWIPE_THRESHOLD) onNextSource()
                    if (dragAccumulator < -SWIPE_THRESHOLD) onPreviousSource()
                    dragAccumulator = 0f
                },
                onDragCancel = { dragAccumulator = 0f },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    dragAccumulator += amount
                },
            )
        }
    } else {
        Modifier
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(288.dp),
        contentAlignment = Alignment.Center,
    ) {
        val side = minOf(maxWidth, maxHeight)
        CoverArt(
            loader = loader,
            track = now.track,
            albumId = now.albumId,
            externalArtwork = now.artwork,
            seed = now.album.ifBlank { now.title },
            corner = LocalMP3roidTheme.current.colors.coverCorner,
            iconSize = 56.dp,
            modifier = Modifier
                .size(side)
                .then(swipe),
        )
    }
}

@Composable
private fun SourceRow(
    sources: List<SourceOption>,
    activeId: String,
    onSelect: (String) -> Unit,
) {
    // Die gerade laufende Quelle steht vorne, dahinter der Rest unverändert.
    val ordered = remember(sources, activeId) {
        val active = sources.firstOrNull { it.id == activeId }
        if (active == null) {
            sources
        } else {
            listOf(active) + sources.filterNot { it.id == activeId }
        }
    }
    val listState = rememberLazyListState()

    // Beim Quellenwechsel zurück an den Anfang, damit die vorne stehende
    // Quelle sichtbar ist, auch wenn vorher in der Leiste gescrollt wurde.
    LaunchedEffect(activeId) { listState.animateScrollToItem(0) }

    // Bewusst ohne key: die Quellen-Id ist der Paketname, und eine App kann
    // mehrere Sessions gleichzeitig offen haben - gleiche Keys würden die
    // Liste mit einem Fehler abbrechen.
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        items(ordered) { source ->
            SourcePill(
                label = source.label,
                icon = null,
                fallbackIcon = if (source.isLocal) R.drawable.ic_headphones else R.drawable.ic_apps,
                selected = source.id == activeId,
                playing = source.isPlaying,
                onClick = { onSelect(source.id) },
            )
        }
    }
}

@Composable
private fun SourcePill(
    label: String,
    icon: ImageBitmap?,
    fallbackIcon: Int,
    selected: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalMP3roidTheme.current
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) theme.colors.accent else theme.colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape),
            )
        } else {
            Icon(
                painter = painterResource(fallbackIcon),
                contentDescription = null,
                tint = if (selected) theme.colors.onAccent else theme.colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) theme.colors.onAccent else theme.colors.textPrimary,
            maxLines = 1,
        )
        if (playing) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (selected) theme.colors.onAccent else theme.colors.accent),
            )
        }
    }
}

@Composable
private fun SeekRow(now: NowPlaying, onSeek: (Long) -> Unit) {
    val theme = LocalMP3roidTheme.current
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val duration = now.durationMs
    val hasDuration = duration > 0L
    val progress = if (hasDuration) (now.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shown = if (dragging) dragValue else progress

    Column(Modifier.fillMaxWidth()) {
        if (now.canSeek && hasDuration) {
            Slider(
                value = shown,
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    dragging = false
                    onSeek((dragValue * duration).toLong())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp),
                colors = SliderDefaults.colors(
                    thumbColor = theme.colors.accent,
                    activeTrackColor = theme.colors.accent,
                    inactiveTrackColor = theme.colors.hairline,
                ),
            )
        } else {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape),
                color = theme.colors.accent,
                trackColor = theme.colors.hairline,
            )
            if (!now.canSeek) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Diese App erlaubt kein Spulen",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.colors.textSecondary.copy(alpha = 0.7f),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (hasDuration) formatTime(now.positionMs) else "--:--",
                style = MaterialTheme.typography.labelSmall,
                color = theme.colors.textSecondary,
            )
            Text(
                text = if (hasDuration) formatTime(duration) else "--:--",
                style = MaterialTheme.typography.labelSmall,
                color = theme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun TransportRow(now: NowPlaying, vm: MP3roidViewModel) {
    val theme = LocalMP3roidTheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MP3roidIconButton(
            iconRes = R.drawable.ic_shuffle,
            contentDescription = "Zufallswiedergabe",
            onClick = vm::toggleShuffle,
            enabled = now.canShuffle,
            tint = if (now.shuffleOn) theme.colors.accent else null,
        )
        MP3roidIconButton(
            iconRes = R.drawable.ic_skip_previous,
            contentDescription = "Vorheriger Titel",
            onClick = vm::previous,
            enabled = now.canSkipPrevious,
            size = 56.dp,
            iconSize = 30.dp,
        )
        MP3roidIconButton(
            iconRes = if (now.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
            contentDescription = if (now.isPlaying) "Pause" else "Abspielen",
            onClick = vm::togglePlayPause,
            enabled = now.canPlayPause,
            filled = true,
            size = theme.colors.controlSize,
            iconSize = 34.dp,
        )
        MP3roidIconButton(
            iconRes = R.drawable.ic_skip_next,
            contentDescription = "Nächster Titel",
            onClick = vm::next,
            enabled = now.canSkipNext,
            size = 56.dp,
            iconSize = 30.dp,
        )
        MP3roidIconButton(
            iconRes = if (now.repeatMode == Player.REPEAT_MODE_ONE) {
                R.drawable.ic_repeat_one
            } else {
                R.drawable.ic_repeat
            },
            contentDescription = "Wiederholen",
            onClick = vm::cycleRepeat,
            enabled = now.canRepeat,
            tint = if (now.repeatMode != Player.REPEAT_MODE_OFF) theme.colors.accent else null,
        )
    }
}

@Composable
private fun SecondaryRow(volume: VolumeState) {
    val theme = LocalMP3roidTheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_volume_up),
            contentDescription = "Lautstärke",
            tint = theme.colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Slider(
            value = volume.current.toFloat(),
            onValueChange = { volume.set(it.toInt()) },
            valueRange = 0f..volume.max.toFloat(),
            modifier = Modifier
                .weight(1f)
                .height(26.dp)
                .padding(horizontal = 10.dp),
            colors = SliderDefaults.colors(
                thumbColor = theme.colors.textPrimary,
                activeTrackColor = theme.colors.textSecondary,
                inactiveTrackColor = theme.colors.hairline,
            ),
        )
    }
}

/**
 * Werkzeuge, die man waehrend der Wiedergabe braucht - direkt am Player,
 * ohne Umweg ueber die Einstellungen. Laufender Sleep-Timer und gesetzter
 * Favorit sind an der gefuellten Form und der Restzeit ablesbar.
 *
 * Gezeigt wird nur, was in der jeweiligen Situation etwas bewirkt: Favoriten
 * und Warteschlange gibt es nur fuer die eigene Bibliothek (ein fremdes Album
 * laesst sich nicht herzen, eine fremde Warteschlange nicht lesen), der
 * Equalizer haengt an der eigenen Audio-Sitzung. Der Sleep-Timer bleibt
 * immer, weil er auch fremde Wiedergabe anhaelt.
 */
@Composable
private fun ToolRow(
    now: NowPlaying,
    favorites: Set<Long>,
    sleepRemainingMs: Long,
    sleepMinutes: Int,
    effectsAvailable: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenEqualizer: () -> Unit,
) {
    val canFavorite = now.isLocal && now.trackId > 0L
    val isFavorite = canFavorite && favorites.contains(now.trackId)
    val timerRunning = sleepRemainingMs > 0L
    val canQueue = now.isLocal
    val canEqualize = effectsAvailable

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canFavorite) {
            ToolButton(
                iconRes = if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
                label = "Favorit",
                contentDescription = if (isFavorite) "Aus Favoriten entfernen" else "Zu Favoriten",
                active = isFavorite,
                onClick = onToggleFavorite,
            )
        }
        ToolButton(
            iconRes = R.drawable.ic_timer,
            label = when {
                timerRunning -> "${(sleepRemainingMs + 59_999L) / 60_000L} Min"
                sleepMinutes > 0 -> "$sleepMinutes Min"
                else -> "Timer"
            },
            contentDescription = "Sleep-Timer",
            active = timerRunning,
            onClick = onOpenSleepTimer,
        )
        if (canQueue) {
            ToolButton(
                iconRes = R.drawable.ic_queue_music,
                label = "Warteschlange",
                contentDescription = "Warteschlange",
                active = false,
                onClick = onOpenQueue,
            )
        }
        if (canEqualize) {
            ToolButton(
                iconRes = R.drawable.ic_equalizer,
                label = "Equalizer",
                contentDescription = "Equalizer",
                active = false,
                onClick = onOpenEqualizer,
            )
        }
    }
}

/**
 * Zeigt die Restzeit des Sleep-Timers direkt am Player, waehrend er laeuft.
 * Antippen oeffnet den Dialog zum Aendern oder Abschalten.
 */
@Composable
private fun SleepTimerPill(remainingMs: Long, onClick: () -> Unit) {
    val theme = LocalMP3roidTheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(theme.colors.surfaceElevated)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_timer),
                contentDescription = null,
                tint = theme.colors.accent,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Sleep-Timer läuft noch ${formatCountdown(remainingMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.colors.accent,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ToolButton(
    iconRes: Int,
    label: String,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalMP3roidTheme.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            // Auch die Beschriftung soll treffen, nicht nur der Kreis.
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        MP3roidIconButton(
            iconRes = iconRes,
            contentDescription = contentDescription,
            onClick = onClick,
            size = 44.dp,
            iconSize = 21.dp,
            filled = active,
            tint = if (active) null else theme.colors.textSecondary,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) theme.colors.accent else theme.colors.textSecondary,
            maxLines = 1,
        )
    }
}

private const val SWIPE_THRESHOLD = 60f
