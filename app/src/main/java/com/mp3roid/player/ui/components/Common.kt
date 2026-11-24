package com.mp3roid.player.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mp3roid.player.R
import com.mp3roid.player.data.ArtworkLoader
import com.mp3roid.player.data.Track
import com.mp3roid.player.ui.theme.LocalMP3roidTheme
import kotlin.math.absoluteValue

// ---------------------------------------------------------------- Formatierung

fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

fun formatTotalTime(ms: Long): String {
    if (ms <= 0L) return "–"
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "$hours Std $minutes Min" else "$minutes Min"
}

/**
 * Restzeit eines Timers als 4:12 bzw. 1:04:12. Aufgerundet, damit am Ende
 * nicht "0:00" stehen bleibt, waehrend noch etwas laeuft.
 */
fun formatCountdown(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) + 999L) / 1_000L
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

fun initialsOf(value: String): String {
    val words = value.trim().split(" ", ",", "-", "(", ")").filter { it.isNotBlank() }
    if (words.isEmpty()) return "♪"
    return when {
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

private fun placeholderBrush(seed: String, gradient: List<Color>): Brush {
    val colors = gradient.filter { it.alpha > 0f }.ifEmpty {
        listOf(Color(0xFF3B3B4A), Color(0xFF20202A))
    }
    val offset = seed.hashCode().absoluteValue % colors.size
    val first = colors[offset]
    val second = colors[(offset + 1) % colors.size]
    return Brush.linearGradient(
        listOf(
            first.copy(alpha = 0.9f),
            second.copy(alpha = 0.85f),
            Color.Black.copy(alpha = 0.45f),
        ),
    )
}

// ------------------------------------------------------------------ Bausteine

@Composable
fun CoverArt(
    loader: ArtworkLoader,
    track: Track? = null,
    albumId: Long = -1L,
    externalArtwork: ImageBitmap? = null,
    seed: String = "",
    corner: Dp = 12.dp,
    iconSize: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    // Die Version des Lademagazins gehört zum Schlüssel: nach dem Wählen
    // eines eigenen Covers zählt sie hoch und alle sichtbaren Bilder laden frisch.
    val artworkVersion by loader.version.collectAsState(initial = 0)
    val bitmap by produceState<ImageBitmap?>(
        initialValue = externalArtwork,
        track?.id,
        albumId,
        externalArtwork to artworkVersion,
    ) {
        value = externalArtwork ?: run {
            if (track != null) {
                loader.artworkFor(track)?.asImageBitmap()
            } else if (albumId >= 0L) {
                loader.albumArt(albumId)?.asImageBitmap()
            } else {
                null
            }
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(placeholderBrush(seed, LocalMP3roidTheme.current.colors.gradient)),
    ) {
        val picture = bitmap
        if (picture != null) {
            Image(
                bitmap = picture,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = initialsOf(seed),
                    color = Color.White.copy(alpha = 0.22f),
                    fontSize = (iconSize.value * 1.1f).sp,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_music_note),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

@Composable
fun RoundCover(loader: ArtworkLoader, seed: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(placeholderBrush(seed, LocalMP3roidTheme.current.colors.gradient)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsOf(seed),
            color = Color.White.copy(alpha = 0.85f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
    }
}

@Composable
fun MP3roidIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    filled: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    val theme = LocalMP3roidTheme.current
    if (filled) {
        FilledIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.size(size),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = theme.colors.accent,
                contentColor = theme.colors.onAccent,
                disabledContainerColor = theme.colors.surfaceElevated,
                disabledContentColor = theme.colors.textSecondary.copy(alpha = 0.4f),
            ),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
            )
        }
    } else {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.size(size),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = tint ?: if (enabled) {
                    theme.colors.textPrimary.copy(alpha = 0.85f)
                } else {
                    theme.colors.textSecondary.copy(alpha = 0.35f)
                },
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    title: String,
    subtitle: String,
    loader: ArtworkLoader,
    track: Track?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val theme = LocalMP3roidTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            loader = loader,
            track = track,
            albumId = track?.albumId ?: -1L,
            seed = title,
            corner = 8.dp,
            iconSize = 18.dp,
            modifier = Modifier.size(50.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = theme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            trailing()
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    val theme = LocalMP3roidTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = theme.colors.textSecondary,
            letterSpacing = 1.4.sp,
        )
        action?.invoke()
    }
}

@Composable
fun EmptyState(iconRes: Int, title: String, hint: String, modifier: Modifier = Modifier) {
    val theme = LocalMP3roidTheme.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = theme.colors.textSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = theme.colors.textPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = theme.colors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
fun SelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalMP3roidTheme.current
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) theme.colors.accent else theme.colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) theme.colors.onAccent else theme.colors.textSecondary,
            maxLines = 1,
        )
    }
}

// ------------------------------------------------------------------- Lautstärke

// Diese beiden Konstanten sind im oeffentlichen SDK nicht sichtbar (sie sind
// @SystemApi). Ihre Texte sind stabil, deshalb stehen sie hier ausgeschrieben.
private const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"

class VolumeState(val current: Int, val max: Int, val set: (Int) -> Unit)
@Composable
fun rememberVolumeState(): VolumeState {
    val context = LocalContext.current
    val manager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val max = remember { manager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15 }
    var current by remember {
        mutableIntStateOf(manager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0)
    }

    // Die Lautstaerketasten aendern den Wert ausserhalb dieser App. Darum wird
    // er mitgelesen: einmal ueber die Systemeinstellung, in die die Tasten
    // immer schreiben, und zusaetzlich ueber den Broadcast, den der Audio-
    // Dienst dabei verschickt. Nur so folgt der Regler auf dem Bildschirm.
    DisposableEffect(manager) {
        val audio = manager
        if (audio == null) {
            onDispose { }
        } else {
            val readVolume = { runCatching { audio.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrNull() }

            val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    readVolume()?.let { current = it }
                }
            }
            runCatching {
                context.contentResolver.registerContentObserver(
                    Settings.System.CONTENT_URI,
                    true,
                    settingsObserver,
                )
            }

            val volumeReceiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context?, intent: Intent?) {
                    val stream = intent?.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1) ?: -1
                    if (stream == -1 || stream == AudioManager.STREAM_MUSIC) {
                        readVolume()?.let { current = it }
                    }
                }
            }
            // Der Broadcast kommt vom System, deshalb nach aussen offen. Der
            // Wert wird nie aus dem Intent uebernommen, sondern immer neu
            // gelesen - gefaelschte Broadcasts richten also nichts an.
            runCatching {
                ContextCompat.registerReceiver(
                    context,
                    volumeReceiver,
                    IntentFilter(ACTION_VOLUME_CHANGED),
                    ContextCompat.RECEIVER_EXPORTED,
                )
            }

            onDispose {
                runCatching { context.contentResolver.unregisterContentObserver(settingsObserver) }
                runCatching { context.unregisterReceiver(volumeReceiver) }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                current = manager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: current
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return VolumeState(current, max) { value ->
        val clamped = value.coerceIn(0, max)
        current = clamped
        runCatching { manager?.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0) }
    }
}
