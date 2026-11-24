package com.mp3roid.player.ui

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mp3roid.player.AppContainer
import com.mp3roid.player.MP3roidApp
import com.mp3roid.player.data.AppEntry
import com.mp3roid.player.data.Album
import com.mp3roid.player.data.Artist
import com.mp3roid.player.data.Folder
import com.mp3roid.player.data.Playlist
import com.mp3roid.player.data.Track
import com.mp3roid.player.launcher.AudioPermission
import com.mp3roid.player.launcher.HomeRole
import com.mp3roid.player.launcher.NotifAccess
import com.mp3roid.player.playback.AudioEffects
import com.mp3roid.player.playback.AudioEffectsState
import com.mp3roid.player.playback.LocalPlaybackState
import com.mp3roid.player.playback.NowPlaying
import com.mp3roid.player.playback.SourceOption
import com.mp3roid.player.ui.theme.MP3roidTheme
import com.mp3roid.player.ui.theme.MP3roidThemes
import com.mp3roid.player.ui.theme.ThemeCatalog
import com.mp3roid.player.ui.theme.UserThemeStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MP3roidViewModel(private val container: AppContainer) : ViewModel() {

    private val app: Context = container.context
    private val settings = container.settings
    private val library = container.library
    private val hub = container.hub
    private val appsRepo = container.apps
    private val overrides = container.overrides
    val artwork = container.artwork

    /** Einmaliger Zustimmungsdialog (RecoverableSecurityException) fürs Löschen. */
    private val _deleteConsent = MutableStateFlow<IntentSender?>(null)
    val deleteConsent: StateFlow<IntentSender?> = _deleteConsent.asStateFlow()
    val playback = container.playback

    // ------------------------------------------------------------------ Theme

    private val userThemes = UserThemeStore(app)

    /**
     * Alle Themes: mitgelieferte plus die selbst gebauten. Bewusst ein
     * laufender Wert und keine einmal gelesene Liste – ein neu angelegtes oder
     * im Editor geändertes Theme soll sofort erscheinen, ohne Neustart.
     */
    private val _themes = MutableStateFlow(ThemeCatalog.all(app))
    val themes: StateFlow<List<MP3roidTheme>> = _themes.asStateFlow()

    private val fallbackTheme: MP3roidTheme = MP3roidThemes.darkVoid

    private val _themeId = settings.themeId
        .stateIn(viewModelScope, SharingStarted.Eagerly, fallbackTheme.id)

    val theme: StateFlow<MP3roidTheme> = combine(_themeId, _themes) { id, list ->
        list.firstOrNull { it.id == id }
            ?: list.firstOrNull { it.id == ThemeCatalog.FALLBACK_ID }
            ?: list.first()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, fallbackTheme)

    val showSystemBars: StateFlow<Boolean> = settings.showSystemBars
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // ------------------------------------------------------------ Hintergrund

    private val _wallpaper = MutableStateFlow<WallpaperState>(WallpaperState.Off)
    val wallpaper: StateFlow<WallpaperState> = _wallpaper.asStateFlow()

    private var wallpaperJob: Job? = null

    val useWallpaper: StateFlow<Boolean> = settings.useWallpaper
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val wallpaperDim: StateFlow<Int> = settings.wallpaperDim
        .stateIn(viewModelScope, SharingStarted.Eagerly, 35)

    val wallpaperSoft: StateFlow<Boolean> = settings.wallpaperSoft
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /**
     * Holt das Bild neu. Wird beim Einschalten, beim Wechseln von „Weich“ und
     * bei jedem Zurückkehren in die App gerufen – so erscheint ein in Androids
     * Tapeten-Auswahl geändertes Bild ohne Neustart.
     */
    fun refreshWallpaper() {
        wallpaperJob?.cancel()
        wallpaperJob = viewModelScope.launch {
            // Bewusst frisch aus dem Speicher gelesen: direkt nach dem Start
            // hat der Flow oben seinen Wert noch nicht erhalten.
            if (!settings.useWallpaper.first()) {
                _wallpaper.value = WallpaperState.Off
                return@launch
            }
            // Das bisherige Bild bleibt stehen, bis das neue fertig gelesen
            // ist – sonst blitzt beim Zurückkehren kurz der Verlauf durch.
            if (_wallpaper.value !is WallpaperState.Ready) {
                _wallpaper.value = WallpaperState.Loading
            }
            _wallpaper.value = loadSystemWallpaper(app, settings.wallpaperSoft.first())
        }
    }

    private fun reloadWallpaper(soft: Boolean) {
        wallpaperJob?.cancel()
        wallpaperJob = viewModelScope.launch {
            if (_wallpaper.value !is WallpaperState.Ready) {
                _wallpaper.value = WallpaperState.Loading
            }
            _wallpaper.value = loadSystemWallpaper(app, soft)
        }
    }

    // ------------------------------------------------------------- Bibliothek

    val tracks: StateFlow<List<Track>> = library.tracks
    val albums: StateFlow<List<Album>> = library.albums
    val artists: StateFlow<List<Artist>> = library.artists
    val folders: StateFlow<List<Folder>> = library.folders
    val scanning: StateFlow<Boolean> = library.scanning
    val musicOnly: StateFlow<Boolean> = settings.musicOnly
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val hideShort: StateFlow<Boolean> = settings.hideShort
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _favorites = MutableStateFlow<Set<Long>>(emptySet())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // ------------------------------------------------------------------- Apps

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()
    private val _appsLoading = MutableStateFlow(true)
    val appsLoading: StateFlow<Boolean> = _appsLoading.asStateFlow()

    // --------------------------------------------------------------- Wiedergabe

    val nowPlaying: StateFlow<NowPlaying?> = hub.nowPlaying
    val sources: StateFlow<List<SourceOption>> = hub.sources
    val localState: StateFlow<LocalPlaybackState> = hub.localState
    val sleepRemainingMs: StateFlow<Long> = hub.sleepRemainingMs
    val effects: StateFlow<AudioEffectsState> = AudioEffects.state
    val sleepMinutes: StateFlow<Int> = settings.sleepMinutes
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // --------------------------------------------------------------- System

    private val _homeRoleHeld = MutableStateFlow(HomeRole.isHeld(app))
    val homeRoleHeld: StateFlow<Boolean> = _homeRoleHeld.asStateFlow()

    private val _notifGranted = MutableStateFlow(NotifAccess.isGranted(app))
    val notifGranted: StateFlow<Boolean> = _notifGranted.asStateFlow()

    private val _audioGranted = MutableStateFlow(AudioPermission.isGranted(app))
    val audioGranted: StateFlow<Boolean> = _audioGranted.asStateFlow()

    private val _onboarded = settings.onboarded
        .map { it as Boolean? }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val onboarded: StateFlow<Boolean?> = _onboarded

    private val _tab = MutableStateFlow(0)
    val tab: StateFlow<Int> = _tab.asStateFlow()

    init {
        hub.setTrackLookup { id -> library.trackById(id) }

        viewModelScope.launch {
            combine(settings.musicOnly, settings.hideShort) { musicOnly, hideShort ->
                musicOnly to hideShort
            }.distinctUntilChanged().collect { (musicOnly, hideShort) ->
                if (AudioPermission.isGranted(app)) {
                    library.scan(musicOnly, hideShort)
                }
            }
        }

        library.startObserving(viewModelScope) {
            viewModelScope.launch { library.scan() }
        }

        viewModelScope.launch {
            settings.favorites.collect { raw ->
                _favorites.value = raw.mapNotNull { it.toLongOrNull() }.toSet()
            }
        }

        viewModelScope.launch {
            settings.playlistsJson.collect { _playlists.value = parsePlaylists(it) }
        }

        viewModelScope.launch {
            combine(
                settings.equalizerEnabled,
                settings.equalizerPreset,
                settings.bassStrength,
            ) { enabled, preset, bass ->
                Triple(enabled, preset, bass)
            }.distinctUntilChanged().collect { (enabled, preset, bass) ->
                AudioEffects.applyStored(enabled, preset, bass)
            }
        }

        refreshApps()
        // Frisch installierte oder entfernte Apps sollen von selbst auftauchen.
        appsRepo.startObserving(viewModelScope) { reloadApps(silent = true) }

        refreshWallpaper()
    }

    // ------------------------------------------------------------- Aktionen

    fun setTab(index: Int) {
        _tab.value = index
    }

    fun setTheme(id: String) {
        viewModelScope.launch { settings.setThemeId(id) }
    }

    /**
     * Legt eine eigene Kopie von [sourceId] an, wählt sie aus und gibt sie
     * zurück. So entsteht ein neues Theme immer aus einer vorhandenen Optik –
     * mit Namen von selbst.
     */
    fun duplicateTheme(sourceId: String): MP3roidTheme? {
        val source = themes.value.firstOrNull { it.id == sourceId } ?: return null
        val copy = source.copy(
            id = userThemes.nextId(themes.value),
            name = userThemes.nextName(themes.value),
            description = "Eigenes Theme · selbst gebaut",
        )
        userThemes.save(copy)
        _themes.value = _themes.value + copy
        setTheme(copy.id)
        return copy
    }

    /** Neues Theme auf Basis der aktuell sichtbaren Optik. */
    fun createTheme(): MP3roidTheme? = duplicateTheme(theme.value.id)

    /**
     * Ändert ein eigenes Theme. Sichtbar ist die Änderung sofort, die Datei
     * wird kurz verzögert geschrieben: beim Ziehen eines Reglers entstehen
     * sonst viele Schreibvorgänge kurz hintereinander.
     */
    fun updateTheme(theme: MP3roidTheme) {
        if (!ThemeCatalog.isOwn(theme.id)) return
        _themes.value = _themes.value.map { if (it.id == theme.id) theme else it }
        pendingThemeSave?.cancel()
        pendingThemeSave = viewModelScope.launch {
            delay(300)
            userThemes.save(theme)
        }
    }

    /** Schreibt sofort – etwa beim Verlassen des Editors. */
    fun persistTheme(theme: MP3roidTheme) {
        if (!ThemeCatalog.isOwn(theme.id)) return
        pendingThemeSave?.cancel()
        pendingThemeSave = null
        userThemes.save(theme)
    }

    fun deleteOwnTheme(id: String) {
        if (!ThemeCatalog.isOwn(id)) return
        pendingThemeSave?.cancel()
        userThemes.delete(id)
        _themes.value = _themes.value.filterNot { it.id == id }
        if (_themeId.value == id) setTheme(fallbackTheme.id)
        _message.value = "Eigenes Theme gelöscht"
    }

    private var pendingThemeSave: Job? = null

    fun refreshPermissions() {
        _homeRoleHeld.value = HomeRole.isHeld(app)
        _notifGranted.value = NotifAccess.isGranted(app)
        val granted = AudioPermission.isGranted(app)
        val wasGranted = _audioGranted.value
        _audioGranted.value = granted
        if (granted && !wasGranted) {
            viewModelScope.launch { library.scan() }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch { settings.setOnboarded(true) }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            val count = library.scan(musicOnly.value, hideShort.value)
            _message.value = "$count Titel gefunden"
        }
    }

    // ------------------------------------------------- Eigene Benennungen

    /**
     * Titel, Albumname und Cover bearbeiten. Alles landet in
     * [TitleOverrides] – die Dateien selbst bleiben unangetastet. Nach einer
     * Namensänderung liest die Bibliothek neu ein, damit die Änderung sofort
     * überall erscheint; ein Cover braucht nur den leeren Bildspeicher.
     */

    fun renameTrack(track: Track, title: String) {
        val clean = title.trim()
        if (clean.isBlank() || clean == track.title) return
        viewModelScope.launch {
            overrides.setTrackTitle(track.id, clean)
            library.scan()
        }
    }

    fun resetTrackTitle(track: Track) {
        viewModelScope.launch {
            overrides.removeTrackTitle(track.id)
            library.scan()
        }
    }

    fun renameAlbum(album: Album, name: String) {
        val clean = name.trim()
        if (clean.isBlank() || clean == album.name) return
        viewModelScope.launch {
            overrides.setAlbumName(album.id, clean)
            library.scan()
        }
    }

    fun resetAlbumName(album: Album) {
        viewModelScope.launch {
            overrides.removeAlbumName(album.id)
            library.scan()
        }
    }

    fun isTrackRenamed(track: Track): Boolean = overrides.hasTrackTitle(track.id)

    fun isAlbumRenamed(album: Album): Boolean = overrides.hasAlbumName(album.id)

    fun hasCustomCover(album: Album): Boolean = overrides.customCover(album.id) != null

    fun setAlbumCover(album: Album, source: Uri) {
        viewModelScope.launch {
            if (overrides.setCover(album.id, source)) artwork.clear()
        }
    }

    fun clearAlbumCover(album: Album) {
        viewModelScope.launch {
            overrides.removeCover(album.id)
            artwork.clear()
        }
    }

    // ------------------------------------------------- Löschen

    /**
     * Löscht die Datei vom Gerät. Nicht-eigene Dateien lösen auf Android 10 einen
     * Zustimmungsdialog aus (RecoverableSecurityException) – der wird über
     * [deleteConsent] an die Oberfläche gereicht und dort gestartet.
     */
    fun deleteTrack(track: Track) {
        viewModelScope.launch {
            try {
                val uri = track.uri
                if (uri.scheme == "file") {
                    File(uri.path ?: "").delete()
                } else {
                    app.contentResolver.delete(uri, null, null)
                }
                // Aufräumen: Favorit, Playlists, eigene Benennungen/Cover
                _favorites.value = _favorites.value - track.id
                viewModelScope.launch { settings.setFavorites(_favorites.value.map { it.toString() }.toSet()) }
                writePlaylists(_playlists.value.map { it.copy(trackIds = it.trackIds - track.id) })
                overrides.removeTrackTitle(track.id)
                if (library.byAlbum(track.albumId).size <= 1) {
                    overrides.removeAlbumName(track.albumId)
                    overrides.removeCover(track.albumId)
                    artwork.clear()
                }
                library.scan()
                _message.value = "Gelöscht"
            } catch (e: RecoverableSecurityException) {
                _deleteConsent.value = e.userAction.actionIntent.intentSender
            } catch (t: Throwable) {
                _message.value = "Löschen fehlgeschlagen: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

    fun onDeleteConsentResult(granted: Boolean) {
        if (_deleteConsent.value == null) return
        _deleteConsent.value = null
        if (!granted) {
            _message.value = "Löschen abgebrochen"
            return
        }
        viewModelScope.launch {
            // Die eigentliche Löschung übernimmt das System, sobald der Nutzer
            // im Zustimmungsdialog bestätigt hat – hier nur neu einlesen.
            library.scan()
            _message.value = "Gelöscht"
        }
    }

    fun refreshApps() = reloadApps(silent = false)

    /** Auffrischen ohne Ladebalken – beim Öffnen der App-Seite und nach Installationen. */
    fun syncApps() = reloadApps(silent = true)

    /** [silent] lässt den Ladebalken weg – für das Nachladen im Hintergrund. */
    private fun reloadApps(silent: Boolean) {
        viewModelScope.launch {
            if (!silent) _appsLoading.value = true
            _apps.value = appsRepo.load()
            if (!silent) _appsLoading.value = false
        }
    }

    fun setMusicOnly(value: Boolean) {
        viewModelScope.launch { settings.setMusicOnly(value) }
    }

    fun setHideShort(value: Boolean) {
        viewModelScope.launch { settings.setHideShort(value) }
    }

    fun setShowSystemBars(value: Boolean) {
        viewModelScope.launch { settings.setShowSystemBars(value) }
    }

    fun setUseWallpaper(value: Boolean) {
        viewModelScope.launch {
            settings.setUseWallpaper(value)
            if (value) {
                reloadWallpaper(settings.wallpaperSoft.first())
            } else {
                wallpaperJob?.cancel()
                _wallpaper.value = WallpaperState.Off
            }
        }
    }

    fun setWallpaperDim(value: Int) {
        viewModelScope.launch { settings.setWallpaperDim(value.coerceIn(0, 80)) }
    }

    fun setWallpaperSoft(value: Boolean) {
        viewModelScope.launch {
            settings.setWallpaperSoft(value)
            if (settings.useWallpaper.first()) reloadWallpaper(value)
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun launchApp(entry: AppEntry) = appsRepo.launch(entry)

    fun appInfo(entry: AppEntry) {
        if (!appsRepo.openInfo(entry)) {
            _message.value = "App-Info ließ sich nicht öffnen"
        }
    }

    fun uninstallApp(entry: AppEntry) {
        if (!appsRepo.uninstall(entry)) {
            _message.value = "Deinstallieren ließ sich nicht öffnen"
        }
    }

    fun playTracks(tracks: List<Track>, startIndex: Int, shuffle: Boolean = false) =
        hub.playTracks(tracks, startIndex, shuffle)

    fun playShuffled(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        hub.playTracks(tracks, 0, shuffle = true)
    }

    fun playNext(track: Track) {
        hub.playNext(track)
        _message.value = "„${track.title}“ kommt als Nächstes"
    }

    fun addToQueue(track: Track) {
        hub.addToQueue(track)
        _message.value = "„${track.title}“ zur Warteschlange hinzugefügt"
    }

    fun togglePlayPause() = hub.togglePlayPause()

    fun next() = hub.next()

    fun previous() = hub.previous()

    fun seekTo(positionMs: Long) = hub.seekTo(positionMs)

    fun toggleShuffle() = hub.toggleShuffle()

    fun cycleRepeat() = hub.cycleRepeat()

    fun selectSource(id: String?) = hub.selectSource(id)

    fun setSleepTimer(minutes: Int) = hub.startSleepTimer(minutes)

    fun cancelSleepTimer() = hub.cancelSleepTimer()

    fun toggleFavorite(trackId: Long) {
        viewModelScope.launch {
            val current = _favorites.value
            val next = if (current.contains(trackId)) current - trackId else current + trackId
            settings.setFavorites(next.map { it.toString() }.toSet())
        }
    }

    fun setEqualizerEnabled(value: Boolean) {
        AudioEffects.setEnabled(value)
        viewModelScope.launch { settings.setEqualizerEnabled(value) }
    }

    fun setEqualizerPreset(index: Int) {
        AudioEffects.setPreset(index)
        viewModelScope.launch { settings.setEqualizerPreset(index) }
    }

    fun setEqualizerBand(index: Int, levelMb: Int) {
        AudioEffects.setBand(index, levelMb)
        viewModelScope.launch { settings.setEqualizerPreset(-1) }
    }

    fun setBassStrength(value: Int) {
        AudioEffects.setBass(value)
        viewModelScope.launch { settings.setBassStrength(value) }
    }

    // ------------------------------------------------------------ Playlists

    fun createPlaylist(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        if (_playlists.value.any { it.name.equals(clean, ignoreCase = true) }) {
            _message.value = "Playlist „$clean“ gibt es schon"
            return
        }
        writePlaylists(_playlists.value + Playlist(clean, emptyList()))
    }

    fun addToPlaylist(playlistName: String, trackId: Long) {
        writePlaylists(
            _playlists.value.map { playlist ->
                if (playlist.name == playlistName && !playlist.trackIds.contains(trackId)) {
                    playlist.copy(trackIds = playlist.trackIds + trackId)
                } else {
                    playlist
                }
            },
        )
        _message.value = "Zu „$playlistName“ hinzugefügt"
    }

    fun removeFromPlaylist(playlistName: String, trackId: Long) {
        writePlaylists(
            _playlists.value.map { playlist ->
                if (playlist.name == playlistName) {
                    playlist.copy(trackIds = playlist.trackIds - trackId)
                } else {
                    playlist
                }
            },
        )
    }

    fun deletePlaylist(playlistName: String) {
        writePlaylists(_playlists.value.filterNot { it.name == playlistName })
    }

    fun tracksOf(playlist: Playlist): List<Track> =
        playlist.trackIds.mapNotNull { library.trackById(it) }

    fun albumById(id: Long): Album? = albums.value.firstOrNull { it.id == id }

    fun artistByName(name: String): Artist? = artists.value.firstOrNull { it.name == name }

    fun folderByPath(path: String): Folder? = folders.value.firstOrNull { it.path == path }

    fun playlistByName(name: String): Playlist? = playlists.value.firstOrNull { it.name == name }

    fun favoriteTracks(): List<Track> = tracks.value.filter { favorites.value.contains(it.id) }

    private fun writePlaylists(list: List<Playlist>) {
        val array = JSONArray()
        list.forEach { playlist ->
            array.put(
                JSONObject()
                    .put("name", playlist.name)
                    .put("tracks", JSONArray(playlist.trackIds)),
            )
        }
        viewModelScope.launch { settings.setPlaylistsJson(array.toString()) }
    }

    private fun parsePlaylists(json: String): List<Playlist> = try {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val name = obj.optString("name").trim()
            if (name.isEmpty()) {
                null
            } else {
                val ids = obj.optJSONArray("tracks")
                Playlist(
                    name = name,
                    trackIds = ids?.let { arr ->
                        (0 until arr.length()).mapNotNull { arr.optLong(it).takeIf { id -> id > 0 } }
                    }.orEmpty(),
                )
            }
        }
    } catch (t: Throwable) {
        emptyList()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MP3roidApp
                MP3roidViewModel(app.container)
            }
        }
    }
}
