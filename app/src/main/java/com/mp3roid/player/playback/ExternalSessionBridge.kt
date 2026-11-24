package com.mp3roid.player.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Was gerade in einer fremden App läuft – Spotify, InnerTune, VLC …
 *
 * `basePositionMs` + `baseElapsedMs` sind eine Momentaufnahme. Fremde Apps
 * melden ihre Position nur sporadisch; deshalb rechnet [positionAt] die
 * Anzeige selbst weiter, damit die Leiste flüssig bleibt.
 *
 * Hinweis: Die Android-Schnittstelle für fremde Wiedergabe kennt bewusst kein
 * Zufall-/Wiederholen-Schalten. Das geht nur für die eigene Wiedergabe.
 */
data class ExternalSource(
    val id: String,
    val packageName: String,
    val appLabel: String,
    val appIcon: ImageBitmap?,
    val title: String,
    val artist: String,
    val album: String,
    val artwork: ImageBitmap?,
    val isPlaying: Boolean,
    val buffering: Boolean,
    val basePositionMs: Long,
    val baseElapsedMs: Long,
    val durationMs: Long,
    val canPlayPause: Boolean,
    val canSkipNext: Boolean,
    val canSkipPrevious: Boolean,
    val canSeek: Boolean,
) {
    fun positionAt(nowElapsedMs: Long): Long {
        val raw = if (isPlaying) basePositionMs + (nowElapsedMs - baseElapsedMs) else basePositionMs
        return if (durationMs > 0L) raw.coerceIn(0L, durationMs) else raw.coerceAtLeast(0L)
    }
}

class ExternalSessionBridge(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val entries = LinkedHashMap<String, Entry>()

    private val _sources = MutableStateFlow<List<ExternalSource>>(emptyList())
    val sources: StateFlow<List<ExternalSource>> = _sources.asStateFlow()

    init {
        scope.launch {
            ExternalSessions.controllers.collect { sync(it) }
        }
    }

    private class Entry(
        val key: String,
        val packageName: String,
        val controller: MediaController,
    ) {
        var callback: MediaController.Callback? = null
        var appLabel: String = packageName
        var appIcon: ImageBitmap? = null
        var appInfoLoaded: Boolean = false
        var title: String = ""
        var artist: String = ""
        var album: String = ""
        var artwork: ImageBitmap? = null
        var artworkUri: Uri? = null
        var artworkLoading: Boolean = false
        var isPlaying: Boolean = false
        var buffering: Boolean = false
        var position: Long = 0L
        var elapsed: Long = 0L
        var duration: Long = 0L
        var actions: Long = 0L
        var stateKnown: Boolean = false

        /**
         * Manche Apps halten eine Session offen, in der nie etwas läuft – etwa
         * ein Player, dessen Bibliothek leer ist. Solche Einträge haben weder
         * Titel noch Zustand und gehören nicht in die Quellenliste.
         */
        fun hasContent(): Boolean =
            title.isNotBlank() || artist.isNotBlank() || album.isNotBlank() ||
                isPlaying || buffering || stateKnown
    }

    // ------------------------------------------------------------- Steuerung

    /**
     * Die Oberfläche kennt eine Quelle nur unter ihrem Paketnamen (das ist die
     * `id` in [ExternalSource]). Intern liegen die Einträge aber unter dem
     * Session-Token, weil eine App mehrere Sessions offen halten kann. Ohne
     * diese Übersetzung liefe jeder Steuerbefehl ins Leere.
     */
    private fun entryOf(id: String): Entry? =
        entries[id]
            ?: entries.values.firstOrNull { it.packageName == id && it.isPlaying }
            ?: entries.values.firstOrNull { it.packageName == id }

    fun togglePlayPause(id: String) {
        val entry = entryOf(id) ?: return
        runCatching {
            if (entry.isPlaying) {
                entry.controller.transportControls.pause()
            } else {
                entry.controller.transportControls.play()
            }
        }
    }

    fun next(id: String) {
        entryOf(id)?.let { runCatching { it.controller.transportControls.skipToNext() } }
    }

    fun previous(id: String) {
        entryOf(id)?.let { runCatching { it.controller.transportControls.skipToPrevious() } }
    }

    fun seekTo(id: String, positionMs: Long) {
        entryOf(id)?.let { runCatching { it.controller.transportControls.seekTo(positionMs) } }
    }

    // ---------------------------------------------------------------- intern

    private fun sync(controllers: List<MediaController>) {
        val wanted = controllers.filter { it.packageName != context.packageName }
        val wantedKeys = wanted.mapNotNull { keyOf(it) }.toSet()

        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item.key !in wantedKeys) {
                item.value.callback?.let { cb ->
                    runCatching { item.value.controller.unregisterCallback(cb) }
                }
                iterator.remove()
            }
        }

        for (controller in wanted) {
            val key = keyOf(controller) ?: continue
            if (entries.containsKey(key)) continue
            val entry = Entry(key, controller.packageName ?: "?", controller)
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    read(entry)
                    publish()
                }

                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    read(entry)
                    publish()
                }

                override fun onSessionDestroyed() {
                    drop(key)
                }
            }
            entry.callback = callback
            runCatching { controller.registerCallback(callback) }
            read(entry)
            entries[key] = entry
        }
        publish()
    }

    private fun keyOf(controller: MediaController): String? = try {
        controller.sessionToken?.toString()
    } catch (t: Throwable) {
        null
    }

    private fun drop(key: String) {
        val entry = entries.remove(key) ?: return
        entry.callback?.let { runCatching { entry.controller.unregisterCallback(it) } }
        publish()
    }

    private fun read(entry: Entry) {
        val metadata = runCatching { entry.controller.metadata }.getOrNull()
        entry.title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        entry.artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()
        entry.album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        entry.duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

        val directArt = runCatching {
            metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        }.getOrNull()

        if (directArt != null) {
            entry.artwork = directArt.asImageBitmap()
            entry.artworkUri = null
        } else {
            val uriText = runCatching {
                metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
            }.getOrNull()
            if (uriText != entry.artworkUri?.toString()) {
                entry.artworkUri = uriText?.let { runCatching { Uri.parse(it) }.getOrNull() }
                entry.artwork = null
                loadArtwork(entry)
            }
        }

        val playbackState = runCatching { entry.controller.playbackState }.getOrNull()
        entry.stateKnown = playbackState != null &&
            playbackState.state != PlaybackState.STATE_NONE
        if (playbackState != null) {
            entry.isPlaying = playbackState.state == PlaybackState.STATE_PLAYING
            entry.buffering = playbackState.state == PlaybackState.STATE_BUFFERING
            entry.position = playbackState.position.coerceAtLeast(0L)
            entry.elapsed = if (playbackState.lastPositionUpdateTime > 0L) {
                playbackState.lastPositionUpdateTime
            } else {
                SystemClock.elapsedRealtime()
            }
            entry.actions = playbackState.actions
        }

        if (!entry.appInfoLoaded) {
            entry.appInfoLoaded = true
            loadAppInfo(entry)
        }
    }

    private fun loadAppInfo(entry: Entry) {
        try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(entry.packageName, 0)
            entry.appLabel = info.loadLabel(pm).toString().ifBlank { entry.packageName }
            val density = context.resources.displayMetrics.density
            val size = (36 * density).toInt().coerceIn(36, 96)
            entry.appIcon = info.loadIcon(pm).toBitmap(size, size, Bitmap.Config.ARGB_8888)
                .asImageBitmap()
        } catch (t: Throwable) {
            entry.appLabel = entry.packageName
        }
    }

    private fun loadArtwork(entry: Entry) {
        val uri = entry.artworkUri ?: return
        if (entry.artworkLoading) return
        entry.artworkLoading = true
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { decode(uri) }
            entry.artworkLoading = false
            if (bitmap != null) {
                entry.artwork = bitmap.asImageBitmap()
                publish()
            }
        }
    }

    private fun decode(uri: Uri): Bitmap? = try {
        val scheme = uri.scheme?.lowercase()
        val bytes = when (scheme) {
            "content", "file", "android.resource" ->
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }

            "http", "https" ->
                (URL(uri.toString()).openConnection() as HttpURLConnection).run {
                    connectTimeout = 6_000
                    readTimeout = 6_000
                    getInputStream().use { it.readBytes() }
                }

            else -> null
        }
        if (bytes == null) {
            null
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            val options = BitmapFactory.Options().apply {
                inSampleSize = if (longest > MAX_ART_SIZE) {
                    Integer.highestOneBit(longest / MAX_ART_SIZE).coerceAtLeast(1)
                } else {
                    1
                }
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    } catch (t: Throwable) {
        null
    }

    private fun publish() {
        _sources.value = entries.values.filter { it.hasContent() }.map { entry ->
            val actions = entry.actions
            val known = actions != 0L
            ExternalSource(
                id = entry.packageName,
                packageName = entry.packageName,
                appLabel = entry.appLabel,
                appIcon = entry.appIcon,
                title = entry.title.ifBlank { entry.appLabel },
                artist = entry.artist,
                album = entry.album,
                artwork = entry.artwork,
                isPlaying = entry.isPlaying,
                buffering = entry.buffering,
                basePositionMs = entry.position,
                baseElapsedMs = entry.elapsed,
                durationMs = entry.duration,
                canPlayPause = !known ||
                    actions.and(
                        PlaybackState.ACTION_PLAY_PAUSE or
                            PlaybackState.ACTION_PLAY or
                            PlaybackState.ACTION_PAUSE,
                    ) != 0L,
                canSkipNext = !known || actions.and(PlaybackState.ACTION_SKIP_TO_NEXT) != 0L,
                canSkipPrevious = !known || actions.and(PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L,
                canSeek = known && entry.duration > 0L &&
                    actions.and(PlaybackState.ACTION_SEEK_TO) != 0L,
            )
        }
    }

    private companion object {
        const val MAX_ART_SIZE = 512
    }
}
