package com.mp3roid.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mp3roid.player.R
import com.mp3roid.player.data.ArtworkLoader
import com.mp3roid.player.playback.NowPlaying
import com.mp3roid.player.ui.theme.LocalMP3roidTheme

@Composable
fun MiniPlayer(
    now: NowPlaying,
    loader: ArtworkLoader,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalMP3roidTheme.current
    val progress = if (now.durationMs > 0L) {
        (now.positionMs.toFloat() / now.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.colors.surfaceElevated)
            .clickable(onClick = onOpen),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(2.dp)
                .background(theme.colors.accent),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverArt(
                loader = loader,
                track = now.track,
                albumId = now.albumId,
                externalArtwork = now.artwork,
                seed = now.album.ifBlank { now.title },
                corner = 8.dp,
                iconSize = 16.dp,
                modifier = Modifier.size(42.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = now.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val second = if (now.isLocal) {
                    now.artist.ifBlank { "Unbekannter Interpret" }
                } else {
                    now.artist.ifBlank { now.sourceLabel }.let { "$it · ${now.sourceLabel}" }
                }
                Text(
                    text = second,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(theme.colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                MP3roidIconButton(
                    iconRes = if (now.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                    contentDescription = if (now.isPlaying) "Pause" else "Abspielen",
                    onClick = onToggle,
                    enabled = now.canPlayPause,
                    size = 38.dp,
                    iconSize = 20.dp,
                    tint = theme.colors.onAccent,
                )
            }
            MP3roidIconButton(
                iconRes = R.drawable.ic_skip_next,
                contentDescription = "Nächster Titel",
                onClick = onNext,
                enabled = now.canSkipNext,
                size = 40.dp,
                iconSize = 22.dp,
            )
        }
    }
}
