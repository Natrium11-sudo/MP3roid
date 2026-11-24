package com.mp3roid.player.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.mp3roidDataStore: DataStore<Preferences> by preferencesDataStore(name = "mp3roid_settings")

/**
 * Alle dauerhaften Einstellungen an einer Stelle.
 */
class SettingsStore(context: Context) {

    private val store = context.mp3roidDataStore

    val themeId: Flow<String> = store.data.map { it[KEY_THEME] ?: "" }
    val onboarded: Flow<Boolean> = store.data.map { it[KEY_ONBOARDED] ?: false }
    val musicOnly: Flow<Boolean> = store.data.map { it[KEY_MUSIC_ONLY] ?: true }
    val hideShort: Flow<Boolean> = store.data.map { it[KEY_HIDE_SHORT] ?: true }
    val showSystemBars: Flow<Boolean> = store.data.map { it[KEY_SHOW_BARS] ?: false }
    val favorites: Flow<Set<String>> = store.data.map { it[KEY_FAVORITES] ?: emptySet() }
    val playlistsJson: Flow<String> = store.data.map { it[KEY_PLAYLISTS] ?: "[]" }
    val equalizerEnabled: Flow<Boolean> = store.data.map { it[KEY_EQ_ON] ?: false }
    val equalizerPreset: Flow<Int> = store.data.map { it[KEY_EQ_PRESET] ?: -1 }
    val bassStrength: Flow<Int> = store.data.map { it[KEY_BASS] ?: 0 }
    val sleepMinutes: Flow<Int> = store.data.map { it[KEY_SLEEP] ?: 0 }
    val useWallpaper: Flow<Boolean> = store.data.map { it[KEY_WALLPAPER] ?: false }
    val wallpaperDim: Flow<Int> = store.data.map { it[KEY_WALLPAPER_DIM] ?: 25 }
    val wallpaperSoft: Flow<Boolean> = store.data.map { it[KEY_WALLPAPER_SOFT] ?: false }

    suspend fun setThemeId(id: String) = store.edit { it[KEY_THEME] = id }
    suspend fun setOnboarded(value: Boolean) = store.edit { it[KEY_ONBOARDED] = value }
    suspend fun setMusicOnly(value: Boolean) = store.edit { it[KEY_MUSIC_ONLY] = value }
    suspend fun setHideShort(value: Boolean) = store.edit { it[KEY_HIDE_SHORT] = value }
    suspend fun setShowSystemBars(value: Boolean) = store.edit { it[KEY_SHOW_BARS] = value }
    suspend fun setFavorites(value: Set<String>) = store.edit { it[KEY_FAVORITES] = value }
    suspend fun setPlaylistsJson(value: String) = store.edit { it[KEY_PLAYLISTS] = value }
    suspend fun setEqualizerEnabled(value: Boolean) = store.edit { it[KEY_EQ_ON] = value }
    suspend fun setEqualizerPreset(value: Int) = store.edit { it[KEY_EQ_PRESET] = value }
    suspend fun setBassStrength(value: Int) = store.edit { it[KEY_BASS] = value }
    suspend fun setSleepMinutes(value: Int) = store.edit { it[KEY_SLEEP] = value }
    suspend fun setUseWallpaper(value: Boolean) = store.edit { it[KEY_WALLPAPER] = value }
    suspend fun setWallpaperDim(value: Int) = store.edit { it[KEY_WALLPAPER_DIM] = value }
    suspend fun setWallpaperSoft(value: Boolean) = store.edit { it[KEY_WALLPAPER_SOFT] = value }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_id")
        val KEY_ONBOARDED = booleanPreferencesKey("onboarded")
        val KEY_MUSIC_ONLY = booleanPreferencesKey("music_only")
        val KEY_HIDE_SHORT = booleanPreferencesKey("hide_short")
        val KEY_SHOW_BARS = booleanPreferencesKey("show_system_bars")
        val KEY_FAVORITES = stringSetPreferencesKey("favorites")
        val KEY_PLAYLISTS = stringPreferencesKey("playlists")
        val KEY_EQ_ON = booleanPreferencesKey("eq_enabled")
        val KEY_EQ_PRESET = intPreferencesKey("eq_preset")
        val KEY_BASS = intPreferencesKey("bass_strength")
        val KEY_SLEEP = intPreferencesKey("sleep_minutes")
        val KEY_WALLPAPER = booleanPreferencesKey("wallpaper")
        val KEY_WALLPAPER_DIM = intPreferencesKey("wallpaper_dim")
        val KEY_WALLPAPER_SOFT = booleanPreferencesKey("wallpaper_soft")
    }
}
