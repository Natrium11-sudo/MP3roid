package com.mp3roid.player.data

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Liest die Musiksammlung des Geräts über den MediaStore und gruppiert sie
 * für die Oberfläche vor. Alles bleibt im Speicher – bei einer privaten
 * Sammlung ist das schneller als jede Datenbank und spart auf einem Gerät
 * mit 2 GB RAM den Datenbank-Overhead.
 */
class LibraryRepository(context: Context, private val overrides: TitleOverrides) {

    private val resolver = context.applicationContext.contentResolver
    private val appContext = context.applicationContext

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    val artists: StateFlow<List<Artist>> = _artists.asStateFlow()

    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var observer: ContentObserver? = null
    private var rescanJob: Job? = null
    private var lastMusicOnly = true
    private var lastHideShort = true

    fun trackById(id: Long): Track? = _tracks.value.firstOrNull { it.id == id }

    fun byAlbum(albumId: Long): List<Track> = _tracks.value.filter { it.albumId == albumId }

    suspend fun scan(musicOnly: Boolean = lastMusicOnly, hideShort: Boolean = lastHideShort): Int {
        lastMusicOnly = musicOnly
        lastHideShort = hideShort
        _scanning.value = true
        return try {
            val loaded = withContext(Dispatchers.IO) { query(musicOnly, hideShort) }
            _tracks.value = loaded
            rebuildGroups(loaded)
            _lastError.value = null
            loaded.size
        } catch (t: Throwable) {
            _lastError.value = t.message ?: t.javaClass.simpleName
            _tracks.value = emptyList()
            rebuildGroups(emptyList())
            0
        } finally {
            _scanning.value = false
        }
    }

    /**
     * Reagiert auf neu hinzugefügte oder gelöschte Dateien. Kurz entprellt,
     * damit ein Kopiervorgang nicht 200 Scans auslöst.
     */
    fun startObserving(scope: CoroutineScope, onChanged: () -> Unit) {
        if (observer != null) return
        val created = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                rescanJob?.cancel()
                rescanJob = scope.launch {
                    delay(1500)
                    onChanged()
                }
            }
        }
        observer = created
        try {
            resolver.registerContentObserver(collectionUri(), true, created)
        } catch (t: Throwable) {
            observer = null
        }
    }

    fun stopObserving() {
        observer?.let {
            try {
                resolver.unregisterContentObserver(it)
            } catch (t: Throwable) {
                // egal
            }
        }
        observer = null
        rescanJob?.cancel()
        rescanJob = null
    }

    private fun collectionUri(): Uri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private fun triggerMediaScan() {
        try {
            val roots = arrayOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath,
            )
            MediaScannerConnection.scanFile(appContext, roots, null, null)
        } catch (t: Throwable) {
            // egal
        }
    }

    private fun query(musicOnly: Boolean, hideShort: Boolean): List<Track> {
        // Manche Recorder (z. B. Fossify) schreiben die Datei, legen aber keinen
        // MediaStore-Eintrag an. Deshalb den MediaScanner anstoßen und kurz warten,
        // damit neu hinzugekommene Dateien in MediaStore erscheinen.
        triggerMediaScan()
        Thread.sleep(MEDIA_SCAN_WAIT_MS)
        val collection = collectionUri()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.RELATIVE_PATH,
        )

        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (musicOnly) {
            conditions += "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        }
        if (hideShort) {
            conditions += "${MediaStore.Audio.Media.DURATION} >= ?"
            args += MIN_DURATION_MS.toString()
        }
        val selection = conditions.joinToString(" AND ").ifEmpty { null }

        val out = ArrayList<Track>(256)
        resolver.query(collection, projection, selection, args.toTypedArray(), SORT_ORDER)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
            val titleIdx = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistIdx = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumIdx = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val albumIdIdx = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            val durationIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val dataIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val trackIdx = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
            val relIdx = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val id = if (idIdx >= 0) cursor.getLong(idIdx) else continue
                val path = dataIdx.takeIf { it >= 0 }?.let { cursor.getString(it) }
                val relative = relIdx.takeIf { it >= 0 }?.let { cursor.getString(it) }
                val folder = folderOf(relative, path)
                val albumId = if (albumIdIdx >= 0) cursor.getLong(albumIdIdx) else -1L
                out += Track(
                    id = id,
                    // Eigene Benennungen überschreiben die Angaben der Mediathek.
                    title = overrides.trackTitle(
                        id,
                        titleIdx.takeIf { it >= 0 }?.let { cursor.getString(it) }.cleanTitle()
                            ?: "Titel $id",
                    ),
                    artist = artistIdx.takeIf { it >= 0 }?.let { cursor.getString(it) }.cleanUnknown(),
                    album = overrides.albumName(
                        albumId,
                        albumIdx.takeIf { it >= 0 }?.let { cursor.getString(it) }.cleanUnknown(),
                    ),
                    albumId = albumId,
                    durationMs = if (durationIdx >= 0) cursor.getLong(durationIdx) else 0L,
                    path = path,
                    folder = folder.first,
                    folderName = folder.second,
                    trackNumber = if (trackIdx >= 0) cursor.getInt(trackIdx) % 1000 else 0,
                    uri = ContentUris.withAppendedId(collection, id),
                )
            }
        }

        // Falls der Filter alles weggefiltert hat (manche Geräte setzen IS_MUSIC falsch),
        // wird freundlicherweise ohne Filter nachgesehen.
        if (out.isEmpty() && (musicOnly || hideShort)) {
            return query(musicOnly = false, hideShort = false)
        }
        return out.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.trackNumber }, { it.title.lowercase() }))
    }

    private fun rebuildGroups(list: List<Track>) {
        _albums.value = list
            .groupBy { it.albumId }
            .map { (id, items) ->
                val first = items.first()
                Album(
                    id = id,
                    name = first.album.ifBlank { "Unbekanntes Album" },
                    artist = first.artist.ifBlank { "Unbekannter Interpret" },
                    tracks = items.sortedWith(compareBy({ it.trackNumber }, { it.title.lowercase() })),
                )
            }
            .sortedWith(compareBy({ it.artist.lowercase() }, { it.name.lowercase() }))

        _artists.value = list
            .groupBy { it.artist }
            .map { (name, items) ->
                Artist(
                    name = name.ifBlank { "Unbekannter Interpret" },
                    tracks = items.sortedWith(compareBy({ it.album.lowercase() }, { it.trackNumber })),
                )
            }
            .sortedBy { it.name.lowercase() }

        _folders.value = list
            .groupBy { it.folder }
            .map { (path, items) ->
                Folder(
                    path = path,
                    name = items.first().folderName.ifBlank { path.ifBlank { "Unbekannt" } },
                    tracks = items.sortedWith(compareBy({ it.trackNumber }, { it.title.lowercase() })),
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    private fun folderOf(relativePath: String?, absolutePath: String?): Pair<String, String> {
        val raw = relativePath?.takeIf { it.isNotBlank() }
            ?: absolutePath?.substringBeforeLast('/', "")?.plus("/")
            ?: ""
        val trimmed = raw.trimEnd('/')
        if (trimmed.isEmpty()) return "" to ""
        val name = trimmed.substringAfterLast('/')
        return trimmed to name.ifBlank { trimmed }
    }

    private fun String?.cleanTitle(): String? = this?.trim()?.takeIf { it.isNotBlank() }

    private fun String?.cleanUnknown(): String {
        val v = this?.trim().orEmpty()
        return if (v.isBlank() || v.equals("<unknown>", ignoreCase = true)) "" else v
    }

    private companion object {
        const val MIN_DURATION_MS = 20_000L
        const val MEDIA_SCAN_WAIT_MS = 3000L
        const val SORT_ORDER = "${MediaStore.Audio.Media.ARTIST} COLLATE NOCASE ASC, " +
            "${MediaStore.Audio.Media.ALBUM} COLLATE NOCASE ASC, " +
            "${MediaStore.Audio.Media.TRACK} ASC"
    }
}
