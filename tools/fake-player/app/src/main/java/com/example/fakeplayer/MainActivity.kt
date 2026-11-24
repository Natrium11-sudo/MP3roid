package com.example.fakeplayer

import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Kleiner Gegenpart zu MP3roid: spielt eine echte Datei aus dem MediaStore und
 * oeffnet dazu eine richtige MediaSession. Damit laesst sich pruefen, ob MP3roid
 * fremde Wiedergabe anzeigt und steuern kann.
 */
class MainActivity : Activity() {

    private class Item(
        val uri: Uri,
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
    )

    private lateinit var session: MediaSession
    private var player: MediaPlayer? = null
    private var items: List<Item> = emptyList()
    private var index = 0
    private var seekAllowed = true
    private var prepared = false

    private lateinit var status: TextView
    private lateinit var seekButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            publishState()
            status.text = buildString {
                append(if (isPlayingNow()) "laeuft: " else "pausiert: ")
                append(current()?.title ?: "-")
                append("\n")
                append("Position ").append(positionMs()).append(" ms von ")
                append(current()?.durationMs ?: 0L).append(" ms")
                append("\nSpulen erlaubt: ").append(if (seekAllowed) "ja" else "nein")
            }
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        session = MediaSession(this, "FakePlayer")
        session.setFlags(
            MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS,
        )
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                android.util.Log.i("FakePlayer", "Callback onPlay")
                startPlayback()
            }

            override fun onPause() {
                android.util.Log.i("FakePlayer", "Callback onPause")
                pausePlayback()
            }

            override fun onStop() {
                android.util.Log.i("FakePlayer", "Callback onStop")
                pausePlayback()
            }

            override fun onSkipToNext() {
                android.util.Log.i("FakePlayer", "Callback onSkipToNext")
                move(1)
            }

            override fun onSkipToPrevious() {
                android.util.Log.i("FakePlayer", "Callback onSkipToPrevious")
                move(-1)
            }

            override fun onSeekTo(pos: Long) {
                android.util.Log.i("FakePlayer", "Callback onSeekTo $pos")
                player?.seekTo(pos.toInt())
                publishState()
            }

            override fun onCommand(
                command: String,
                args: Bundle?,
                result: android.os.ResultReceiver?,
            ) {
                android.util.Log.i("FakePlayer", "Callback onCommand $command")
                super.onCommand(command, args, result)
            }
        })

        items = loadItems()
        status = TextView(this).apply { textSize = 16f }
        seekButton = Button(this).apply {
            text = "Spulen erlauben: ja"
            setOnClickListener {
                seekAllowed = !seekAllowed
                text = "Spulen erlauben: " + if (seekAllowed) "ja" else "nein"
                publishState()
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
            addView(status)
            addView(Button(this@MainActivity).apply {
                text = "Play/Pause"
                setOnClickListener { if (isPlayingNow()) pausePlayback() else startPlayback() }
            })
            addView(Button(this@MainActivity).apply {
                text = "Weiter"
                setOnClickListener { move(1) }
            })
            addView(seekButton)
            addView(Button(this@MainActivity).apply {
                text = "MP3roid in den Vordergrund"
                setOnClickListener {
                    packageManager.getLaunchIntentForPackage("com.mp3roid.player")
                        ?.let { startActivity(it) }
                }
            })
        }
        setContentView(root)

        session.isActive = true
        handler.post(ticker)
        playIndex(0)
    }

    override fun onResume() {
        super.onResume()
        session.isActive = true
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        player?.release()
        player = null
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ Daten

    private fun loadItems(): List<Item> {
        val out = ArrayList<Item>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
        )
        runCatching {
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Audio.Media.TITLE + " ASC",
            )?.use { cursor ->
                val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    out += Item(
                        uri = ContentUris.withAppendedId(collection, id),
                        title = cursor.getString(1) ?: "Titel $id",
                        artist = cursor.getString(2) ?: "",
                        album = cursor.getString(3) ?: "",
                        durationMs = cursor.getLong(4),
                    )
                }
            }
        }
        return out
    }

    private fun current(): Item? = items.getOrNull(index)

    // -------------------------------------------------------------- Wiedergabe

    private fun playIndex(target: Int) {
        if (items.isEmpty()) {
            status.text = "Keine Audiodateien im MediaStore gefunden"
            return
        }
        index = ((target % items.size) + items.size) % items.size
        prepared = false
        player?.release()
        val item = current() ?: return
        val mp = MediaPlayer()
        player = mp
        mp.setOnPreparedListener {
            prepared = true
            it.start()
            publishMetadata()
            publishState()
        }
        mp.setOnCompletionListener { move(1) }
        mp.setOnErrorListener { _, what, extra ->
            status.text = "Fehler beim Abspielen ($what/$extra)"
            true
        }
        runCatching {
            mp.setDataSource(this, item.uri)
            mp.prepareAsync()
        }.onFailure {
            status.text = "Konnte ${item.uri} nicht oeffnen: ${it.message}"
        }
    }

    private fun move(delta: Int) = playIndex(index + delta)

    private fun startPlayback() {
        val mp = player
        if (mp == null || !prepared) {
            playIndex(index)
            return
        }
        runCatching { mp.start() }
        publishState()
    }

    private fun pausePlayback() {
        runCatching { player?.pause() }
        publishState()
    }

    private fun isPlayingNow(): Boolean = runCatching { player?.isPlaying == true }.getOrDefault(false)

    private fun positionMs(): Long = runCatching { player?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)

    // ------------------------------------------------------------ MediaSession

    private fun publishMetadata() {
        val item = current() ?: return
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, item.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, item.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, item.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, item.durationMs)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork(item))
                .build(),
        )
    }

    private fun publishState() {
        var actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_STOP
        if (seekAllowed) actions = actions or PlaybackState.ACTION_SEEK_TO

        val state = when {
            !prepared -> PlaybackState.STATE_BUFFERING
            isPlayingNow() -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(state, positionMs(), 1f, SystemClock.elapsedRealtime())
                .build(),
        )
        if (current() != null) publishMetadata()
    }

    /** Sichtbares Cover, damit sich der Bildpfad pruefen laesst. */
    private fun artwork(item: Item): Bitmap {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.color = if (index % 2 == 0) Color.rgb(0x14, 0x5A, 0x8C) else Color.rgb(0x8C, 0x2A, 0x14)
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        paint.color = Color.WHITE
        paint.textSize = 48f
        canvas.drawText("FREMd", 24f, 140f, paint)
        return bitmap
    }
}
