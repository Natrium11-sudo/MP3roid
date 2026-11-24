package com.mp3roid.player.playback

import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import com.mp3roid.player.data.SettingsStore
import com.mp3roid.player.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Ein einziger Zustand für „was läuft gerade", egal ob es eine lokale MP3 ist
 * oder Spotify über den Benachrichtigungszugriff.
 */
data class NowPlaying(
    val sourceId: String,
    val sourceLabel: String,
    val sourcePackage: String?,
    val sourceIcon: ImageBitmap?,
    val isLocal: Boolean,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUri: Uri?,
    val artwork: ImageBitmap?,
    val albumId: Long,
    val trackId: Long,
    val track: Track?,
    val isPlaying: Boolean,
    val buffering: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val canPlayPause: Boolean,
    val canSkipNext: Boolean,
    val canSkipPrevious: Boolean,
    val canSeek: Boolean,
    val canShuffle: Boolean,
    val canRepeat: Boolean,
    val shuffleOn: Boolean,
    val repeatMode: Int,
)

data class SourceOption(
    val id: String,
    val label: String,
    val packageName: String?,
    val isLocal: Boolean,
    val isPlaying: Boolean,
)

class PlayerHub(
    private val playback: PlaybackConnection,
    private val external: ExternalSessionBridge,
    private val settings: SettingsStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val selected = MutableStateFlow<String?>(null)

    private var trackLookup: (Long) -> Track? = { null }

    private val _sleepRemainingMs = MutableStateFlow(0L)
    val sleepRemainingMs: StateFlow<Long> = _sleepRemainingMs.asStateFlow()

    private var sleepJob: Job? = null

    /** Der Ticker treibt nur die Anzeige – gesteuert wird nichts von hier aus. */
    private val tick = flow {
        while (true) {
            emit(SystemClock.elapsedRealtime())
            delay(TICK_MS)
        }
    }

    val localState: StateFlow<LocalPlaybackState> = playback.state

    val sources: StateFlow<List<SourceOption>> =
        combine(playback.state, external.sources) { local, ext ->
            buildList {
                if (local.hasItem) {
                    add(SourceOption(LOCAL_ID, LOCAL_LABEL, null, true, local.isPlaying))
                }
                ext.forEach { source ->
                    add(
                        SourceOption(
                            id = source.id,
                            label = source.appLabel,
                            packageName = source.packageName,
                            isLocal = false,
                            isPlaying = source.isPlaying,
                        ),
                    )
                }
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val nowPlaying: StateFlow<NowPlaying?> =
        combine(playback.state, external.sources, selected, tick) { local, ext, sel, now ->
            build(local, ext, sel, now)
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        playback.connect()
    }

    /** Damit die Oberfläche lokale Titel-IDs wieder zu Objekten auflösen kann. */
    fun setTrackLookup(lookup: (Long) -> Track?) {
        trackLookup = lookup
    }

    // ------------------------------------------------------------- Steuerung

    fun selectSource(id: String?) {
        selected.value = id
    }

    fun togglePlayPause() {
        when (val id = activeId() ?: return) {
            LOCAL_ID -> playback.togglePlayPause()
            else -> external.togglePlayPause(id)
        }
    }

    fun next() {
        when (val id = activeId() ?: return) {
            LOCAL_ID -> playback.next()
            else -> external.next(id)
        }
    }

    fun previous() {
        when (val id = activeId() ?: return) {
            LOCAL_ID -> playback.previous()
            else -> external.previous(id)
        }
    }

    fun seekTo(positionMs: Long) {
        when (val id = activeId() ?: return) {
            LOCAL_ID -> playback.seekTo(positionMs)
            else -> external.seekTo(id, positionMs)
        }
    }

    /**
     * Zufall und Wiederholen steuern nur die eigene Wiedergabe. Die
     * Android-Schnittstelle für fremde Sessions kennt diese Schalter nicht,
     * deshalb bleiben sie dort einfach wirkungslos (und werden ausgegraut).
     */
    fun toggleShuffle() {
        if (activeId() == LOCAL_ID) playback.setShuffle(!playback.state.value.shuffle)
    }

    fun cycleRepeat() {
        if (activeId() == LOCAL_ID) playback.cycleRepeat()
    }

    fun playTracks(tracks: List<Track>, startIndex: Int, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        selected.value = LOCAL_ID
        playback.playTracks(tracks, startIndex, shuffle)
    }

    fun addToQueue(track: Track) = playback.addToQueue(track)

    fun playNext(track: Track) = playback.playNext(track)

    fun clearQueue() = playback.stop()

    // ----------------------------------------------------------- Sleep-Timer

    fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        scope.launch { settings.setSleepMinutes(minutes) }
        if (minutes <= 0) {
            _sleepRemainingMs.value = 0L
            return
        }
        sleepJob = scope.launch {
            var remaining = minutes * 60_000L
            while (remaining > 0L) {
                _sleepRemainingMs.value = remaining
                delay(1_000L)
                remaining -= 1_000L
            }
            _sleepRemainingMs.value = 0L
            pauseEverything()
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepRemainingMs.value = 0L
        scope.launch { settings.setSleepMinutes(0) }
    }

    private fun pauseEverything() {
        if (playback.state.value.isPlaying) playback.togglePlayPause()
        external.sources.value.filter { it.isPlaying }.forEach { external.togglePlayPause(it.id) }
    }

    // ---------------------------------------------------------------- intern

    private fun activeId(): String? =
        pick(playback.state.value, external.sources.value, selected.value)

    private fun pick(
        local: LocalPlaybackState,
        ext: List<ExternalSource>,
        selection: String?,
    ): String? {
        val playingExternal = ext.firstOrNull { it.isPlaying }
        if (local.isPlaying || playingExternal != null) {
            if (selection == LOCAL_ID && local.isPlaying) return LOCAL_ID
            if (selection != null && ext.any { it.id == selection && it.isPlaying }) return selection
            if (local.isPlaying) return LOCAL_ID
            if (playingExternal != null) return playingExternal.id
        }
        if (selection != null) {
            if (selection == LOCAL_ID && local.hasItem) return LOCAL_ID
            if (ext.any { it.id == selection }) return selection
        }
        if (local.hasItem) return LOCAL_ID
        return ext.firstOrNull()?.id
    }

    private fun build(
        local: LocalPlaybackState,
        ext: List<ExternalSource>,
        selection: String?,
        now: Long,
    ): NowPlaying? {
        return when (val id = pick(local, ext, selection)) {
            null -> null

            LOCAL_ID -> NowPlaying(
                sourceId = LOCAL_ID,
                sourceLabel = LOCAL_LABEL,
                sourcePackage = null,
                sourceIcon = null,
                isLocal = true,
                title = local.title.ifBlank { "Unbekannter Titel" },
                artist = local.artist,
                album = local.album,
                artworkUri = local.artworkUri,
                artwork = null,
                albumId = local.albumId,
                trackId = local.trackId,
                track = trackLookup(local.trackId),
                isPlaying = local.isPlaying,
                buffering = local.isBuffering,
                positionMs = local.positionMs,
                durationMs = local.durationMs,
                canPlayPause = true,
                canSkipNext = local.queueSize > 1,
                canSkipPrevious = local.queueSize > 1,
                canSeek = local.durationMs > 0L,
                canShuffle = local.queueSize > 1,
                canRepeat = local.queueSize > 1,
                shuffleOn = local.shuffle,
                repeatMode = local.repeatMode,
            )

            else -> {
                val source = ext.firstOrNull { it.id == id } ?: return null
                NowPlaying(
                    sourceId = source.id,
                    sourceLabel = source.appLabel,
                    sourcePackage = source.packageName,
                    sourceIcon = source.appIcon,
                    isLocal = false,
                    title = source.title.ifBlank { source.appLabel },
                    artist = source.artist,
                    album = source.album,
                    artworkUri = null,
                    artwork = source.artwork,
                    albumId = -1L,
                    trackId = -1L,
                    track = null,
                    isPlaying = source.isPlaying,
                    buffering = source.buffering,
                    positionMs = source.positionAt(now),
                    durationMs = source.durationMs,
                    canPlayPause = source.canPlayPause,
                    canSkipNext = source.canSkipNext,
                    canSkipPrevious = source.canSkipPrevious,
                    canSeek = source.canSeek,
                    canShuffle = false,
                    canRepeat = false,
                    shuffleOn = false,
                    repeatMode = 0,
                )
            }
        }
    }

    companion object {
        const val LOCAL_ID = "local"
        const val LOCAL_LABEL = "Lokal"
        private const val TICK_MS = 500L
    }
}
