package com.mp3roid.player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Eigene Namen und Cover-Bilder, die nur in MP3roid gelten.
 *
 * Android verbietet Apps ohne besonderes Recht, die Tags fremder Dateien
 * nachträglich umzuschreiben – und eine Änderung in der Datei selbst würde
 * auch die anderen Player auf dem Gerät verändern. Deshalb merkt sich
 * MP3roid seine Korrekturen selbst: ein kleines JSON-Verzeichnis im
 * privaten Speicher, die gewählten Cover als eigene Bilder darin.
 *
 * Die Bibliothek wendet die Namen beim Einlesen an (LibraryRepository),
 * das Cover-Lademagazin prüft den eigenen Ordner zuerst (ArtworkLoader).
 */
class TitleOverrides(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "overrides.json")
    val coversDir: File = File(appContext.filesDir, "covers")

    private val lock = Any()

    @Volatile
    private var albumNames: Map<Long, String> = emptyMap()

    @Volatile
    private var trackTitles: Map<Long, String> = emptyMap()

    /** Einmalig beim Start lesen; bleibt die Datei unlesbar, gelten die Originalnamen. */
    fun load() {
        try {
            val text = file.takeIf { it.exists() }?.readText().orEmpty()
            if (text.isBlank()) return
            val json = JSONObject(text)
            albumNames = json.optJSONObject(KEY_ALBUMS).toLongMap()
            trackTitles = json.optJSONObject(KEY_TRACKS).toLongMap()
        } catch (t: Throwable) {
            albumNames = emptyMap()
            trackTitles = emptyMap()
        }
    }

    fun albumName(albumId: Long, fallback: String): String = albumNames[albumId] ?: fallback

    fun trackTitle(trackId: Long, fallback: String): String = trackTitles[trackId] ?: fallback

    fun hasAlbumName(albumId: Long): Boolean = albumNames.containsKey(albumId)

    fun hasTrackTitle(trackId: Long): Boolean = trackTitles.containsKey(trackId)

    /** Die eigene Cover-Datei eines Albums, sofern der Nutzer eine gewählt hat. */
    fun customCover(albumId: Long): File? =
        File(coversDir, "$albumId.jpg").takeIf { it.isFile && it.length() > 0L }

    suspend fun setAlbumName(albumId: Long, name: String) = write {
        albumNames = albumNames + (albumId to name)
    }

    suspend fun removeAlbumName(albumId: Long) = write {
        albumNames = albumNames - albumId
    }

    suspend fun setTrackTitle(trackId: Long, title: String) = write {
        trackTitles = trackTitles + (trackId to title)
    }

    suspend fun removeTrackTitle(trackId: Long) = write {
        trackTitles = trackTitles - trackId
    }

    /**
     * Gewähltes Bild als kompaktes JPEG sichern. Höchstens [COVER_MAX] Pixel
     * halten den Speicherbedarf klein, Transparenz wird auf Weiß gelegt, damit
     * JPEG keinen schwarzen Hintergrund ergibt.
     */
    suspend fun setCover(albumId: Long, source: Uri): Boolean = withContext(Dispatchers.IO) {
        var tmp: File? = null
        try {
            coversDir.mkdirs()
            val target = File(coversDir, "$albumId.jpg")
            tmp = File(coversDir, "$albumId.tmp")
            val resolver = appContext.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > COVER_MAX) sample *= 2

            val decoded = resolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext false
            val scaled = if (maxOf(decoded.width, decoded.height) > COVER_MAX) {
                scaleDown(decoded)
            } else {
                decoded
            }
            val flat = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
            Canvas(flat).apply {
                drawColor(Color.WHITE)
                drawBitmap(scaled, 0f, 0f, null)
            }
            if (flat !== scaled) scaled.recycle()

            tmp.outputStream().use { out -> flat.compress(Bitmap.CompressFormat.JPEG, 90, out) }
            flat.recycle()

            target.delete()
            if (!tmp.renameTo(target)) return@withContext false
            true
        } catch (t: Throwable) {
            try {
                tmp?.delete()
            } catch (ignored: Throwable) {
                // egal
            }
            false
        }
    }

    suspend fun removeCover(albumId: Long): Unit = withContext(Dispatchers.IO) {
        try {
            File(coversDir, "$albumId.jpg").delete()
        } catch (t: Throwable) {
            // egal
        }
    }

    /** Änderung und Schreiben gehören zusammen, damit keine Schreibkonfurrenz entsteht. */
    private suspend fun write(change: () -> Unit) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            change()
            persistLocked()
        }
    }

    private fun persistLocked() {
        try {
            if (albumNames.isEmpty() && trackTitles.isEmpty()) {
                file.delete()
                return
            }
            val json = JSONObject()
            json.put(
                KEY_ALBUMS,
                JSONObject().also { obj -> albumNames.forEach { (id, name) -> obj.put(id.toString(), name) } },
            )
            json.put(
                KEY_TRACKS,
                JSONObject().also { obj -> trackTitles.forEach { (id, title) -> obj.put(id.toString(), title) } },
            )
            val staged = File(file.parentFile, "${file.name}.tmp")
            staged.writeText(json.toString())
            if (!staged.renameTo(file)) {
                file.delete()
                staged.renameTo(file)
            }
        } catch (t: Throwable) {
            // Schreiben gescheitert: die Änderung gilt dann bis zum Neustart.
        }
    }

    private fun scaleDown(src: Bitmap): Bitmap {
        val ratio = COVER_MAX.toFloat() / maxOf(src.width, src.height)
        val scaled = Bitmap.createScaledBitmap(
            src,
            (src.width * ratio).toInt().coerceAtLeast(1),
            (src.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== src) src.recycle()
        return scaled
    }

    private fun JSONObject?.toLongMap(): Map<Long, String> {
        val obj = this ?: return emptyMap()
        val out = LinkedHashMap<Long, String>()
        for (key in obj.keys()) {
            val id = key.toLongOrNull() ?: continue
            out[id] = obj.getString(key)
        }
        return out
    }

    private companion object {
        const val COVER_MAX = 1024
        const val KEY_ALBUMS = "albums"
        const val KEY_TRACKS = "tracks"
    }
}
