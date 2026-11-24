package com.mp3roid.player.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lädt Cover-Bilder in drei Stufen, damit auch schlecht getaggte MP3s etwas
 * anzeigen:
 *
 * 1. eigenes Cover, wenn der Nutzer für das Album eines gewählt hat
 * 2. Album-Art aus dem MediaStore
 * 3. eingebettetes Bild aus der Datei selbst
 * 4. Miniaturansicht des MediaStore-Eintrags
 *
 * Fällt alles aus, zeichnet die Oberfläche einen Farbverlauf aus dem Titel.
 *
 * [version] zählt bei jedem Leeren des Zwischenspeichers hoch; die Oberfläche
 * hängt sie an ihre Bildzustände, damit neu gewählte Cover sofort ersetzt
 * werden und nicht das alte Bild weiter anzeigen.
 */
class ArtworkLoader(context: Context, private val overrides: TitleOverrides) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private val cache = object : LruCache<String, Bitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun artworkFor(track: Track): Bitmap? = withContext(Dispatchers.IO) {
        val key = "t${track.id}"
        cache.get(key)?.let { return@withContext it }
        val bitmap = albumArt(track.albumId)
            ?: embeddedArt(track)
            ?: thumbnail(track.uri)
            ?: return@withContext null
        cache.put(key, bitmap)
        bitmap
    }

    fun albumArt(albumId: Long): Bitmap? {
        if (albumId < 0) return null
        val key = "a$albumId"
        cache.get(key)?.let { return it }
        val bitmap = customArt(albumId) ?: mediaArt(albumId)?.let { downscale(it) }
        if (bitmap != null) cache.put(key, bitmap)
        return bitmap
    }

    /** Eigenes Cover des Nutzers; schlägt jede andere Quelle. */
    private fun customArt(albumId: Long): Bitmap? {
        val file = overrides.customCover(albumId) ?: return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)?.let { downscale(it) }
        } catch (t: Throwable) {
            null
        }
    }

    private fun mediaArt(albumId: Long): Bitmap? = try {
        val uri = ContentUris.withAppendedId(ALBUM_ART_URI, albumId)
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    } catch (t: Throwable) {
        null
    }

    private fun embeddedArt(track: Track): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            val path = track.path
            if (!path.isNullOrBlank() && File(path).exists()) {
                retriever.setDataSource(path)
            } else {
                retriever.setDataSource(appContext, track.uri)
            }
            retriever.embeddedPicture?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { downscale(it) }
            }
        } catch (t: Throwable) {
            null
        } finally {
            try {
                retriever.release()
            } catch (t: Throwable) {
                // ignorieren
            }
        }
    }

    private fun thumbnail(uri: Uri): Bitmap? = try {
        resolver.loadThumbnail(uri, Size(THUMB_SIZE, THUMB_SIZE), null)?.let { downscale(it) }
    } catch (t: Throwable) {
        null
    }

    private fun downscale(src: Bitmap): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= MAX_SIZE) return src
        val ratio = MAX_SIZE.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            src,
            (src.width * ratio).toInt().coerceAtLeast(1),
            (src.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== src) src.recycle()
        return scaled
    }

    fun clear() {
        cache.evictAll()
        _version.value++
    }

    private companion object {
        const val MAX_SIZE = 512
        const val THUMB_SIZE = 512
        val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")

        /** Rund ein Zwölftel des Heaps – auf einem 2-GB-Gerät etwa 15 MB. */
        fun cacheSizeKb(): Int =
            (Runtime.getRuntime().maxMemory() / 1024 / 12).toInt().coerceIn(4 * 1024, 32 * 1024)
    }
}
