package com.mp3roid.player.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.mp3roid.player.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class QueueItem(
    val trackId: Long,
    val title: String,
    val artist: String,
)

data class LocalPlaybackState(
    val connected: Boolean = false,
    val hasItem: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumId: Long = -1L,
    val trackId: Long = -1L,
    val artworkUri: Uri? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queueIndex: Int = 0,
    val queueSize: Int = 0,
    val upNext: List<QueueItem> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * Verbindet die Oberfläche mit dem [PlaybackService] und übersetzt die
 * Media3-Welt in ein schlichtes Zustandsobjekt.
 */
class PlaybackConnection(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private var ticker: Job? = null

    private val _state = MutableStateFlow(LocalPlaybackState())
    val state: StateFlow<LocalPlaybackState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish(player)
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(errorMessage = error.message ?: "Wiedergabefehler")
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            AudioEffects.attach(controller?.audioSessionId ?: 0)
            if (isPlaying) startTicker() else stopTicker()
            controller?.let { publish(it) }
        }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val pending = MediaController.Builder(context, token).buildAsync()
        future = pending
        pending.addListener(
            {
                try {
                    val c = pending.get()
                    controller = c
                    c.addListener(listener)
                    publish(c)
                    if (c.isPlaying) startTicker()
                } catch (t: Throwable) {
                    _state.value = _state.value.copy(
                        connected = false,
                        errorMessage = t.message ?: "Player nicht erreichbar",
                    )
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun release() {
        stopTicker()
        controller?.removeListener(listener)
        controller = null
        future?.let { runCatching { MediaController.releaseFuture(it) } }
        future = null
    }

    // ---------------------------------------------------------------- Steuerung

    fun playTracks(tracks: List<Track>, startIndex: Int, shuffle: Boolean = false) {
        val c = controller ?: return
        val items = tracks.map(::mediaItemFor)
        c.setMediaItems(items, startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)), 0L)
        c.shuffleModeEnabled = shuffle
        c.prepare()
        c.play()
    }

    fun addToQueue(track: Track) {
        val c = controller ?: return
        c.addMediaItem(mediaItemFor(track))
    }

    fun playNext(track: Track) {
        val c = controller ?: return
        val index = (c.currentMediaItemIndex + 1).coerceAtLeast(0)
        c.addMediaItem(index, mediaItemFor(track))
    }

    fun jumpToQueueIndex(index: Int) {
        controller?.let {
            it.seekTo(index, 0L)
            it.play()
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        controller?.let {
            if (it.hasNextMediaItem()) it.seekToNextMediaItem() else it.seekTo(0L)
        }
    }

    fun previous() {
        controller?.let {
            if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() else it.seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun setShuffle(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun stop() {
        controller?.let {
            it.pause()
            it.clearMediaItems()
        }
    }

    fun queueTrackIds(): List<Long> {
        val c = controller ?: return emptyList()
        return (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).mediaId.toLongOrNull() }
    }

    // ------------------------------------------------------------------ intern

    private fun mediaItemFor(track: Track): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist.ifBlank { null })
            .setAlbumTitle(track.album.ifBlank { null })
            .setArtworkUri(albumArtUri(track.albumId))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
        return MediaItem.Builder()
            .setUri(track.uri)
            .setMediaId(track.id.toString())
            .setMediaMetadata(metadata)
            .build()
    }

    private fun albumArtUri(albumId: Long): Uri? {
        if (albumId < 0) return null
        return Uri.parse("content://media/external/audio/albumart/$albumId")
    }

    private fun publish(player: Player) {
        val item = player.currentMediaItem
        val currentIndex = player.currentMediaItemIndex
        val count = player.mediaItemCount
        val upNext = (currentIndex + 1 until minOf(count, currentIndex + 1 + UP_NEXT_LIMIT)).map { i ->
            val mediaItem = player.getMediaItemAt(i)
            QueueItem(
                trackId = mediaItem.mediaId.toLongOrNull() ?: -1L,
                title = mediaItem.mediaMetadata.title?.toString().orEmpty(),
                artist = mediaItem.mediaMetadata.artist?.toString().orEmpty(),
            )
        }
        val duration = player.duration
        _state.value = LocalPlaybackState(
            connected = true,
            hasItem = item != null,
            title = item?.mediaMetadata?.title?.toString().orEmpty(),
            artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
            album = item?.mediaMetadata?.albumTitle?.toString().orEmpty(),
            albumId = albumIdOf(item?.mediaMetadata?.artworkUri),
            trackId = item?.mediaId?.toLongOrNull() ?: -1L,
            artworkUri = item?.mediaMetadata?.artworkUri,
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = if (duration == C.TIME_UNSET) 0L else duration.coerceAtLeast(0L),
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            queueIndex = currentIndex,
            queueSize = count,
            upNext = upNext,
            errorMessage = null,
        )
    }

    private fun albumIdOf(artUri: Uri?): Long =
        artUri?.lastPathSegment?.toLongOrNull() ?: -1L

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (true) {
                val c = controller ?: break
                if (!c.isPlaying) break
                _state.value = _state.value.copy(
                    positionMs = c.currentPosition.coerceAtLeast(0L),
                    durationMs = c.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: _state.value.durationMs,
                )
                delay(TICK_MS)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private companion object {
        const val TICK_MS = 500L
        const val UP_NEXT_LIMIT = 40
    }
}
