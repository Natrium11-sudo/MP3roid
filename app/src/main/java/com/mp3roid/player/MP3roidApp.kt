package com.mp3roid.player

import android.app.Application
import android.content.Context
import com.mp3roid.player.data.AppsRepository
import com.mp3roid.player.data.ArtworkLoader
import com.mp3roid.player.data.LibraryRepository
import com.mp3roid.player.data.SettingsStore
import com.mp3roid.player.data.TitleOverrides
import com.mp3roid.player.playback.ExternalSessionBridge
import com.mp3roid.player.playback.PlaybackConnection
import com.mp3roid.player.playback.PlayerHub

class MP3roidApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * Bewusst handgeschriebene Abhängigkeitsverwaltung: kein Hilt, kein KSP.
 * Das hält den Build klein und den Start auf schwacher Hardware schnell.
 */
class AppContainer(val context: Context) {
    val settings: SettingsStore by lazy { SettingsStore(context) }

    /** Eigene Namen und Cover – wird vor Bibliothek und Cover-Lader gelesen. */
    val overrides: TitleOverrides by lazy { TitleOverrides(context).also { it.load() } }

    val library: LibraryRepository by lazy { LibraryRepository(context, overrides) }
    val artwork: ArtworkLoader by lazy { ArtworkLoader(context, overrides) }
    val apps: AppsRepository by lazy { AppsRepository(context) }
    val playback: PlaybackConnection by lazy { PlaybackConnection(context) }
    val external: ExternalSessionBridge by lazy { ExternalSessionBridge(context) }
    val hub: PlayerHub by lazy { PlayerHub(playback, external, settings) }
}
