package com.mp3roid.player.data

import android.net.Uri

/** Ein einzelner Titel aus der Geräte-Mediathek. */
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val path: String?,
    val folder: String,
    val folderName: String,
    val trackNumber: Int,
    val uri: Uri,
)

data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val tracks: List<Track>,
) {
    val durationMs: Long get() = tracks.sumOf { it.durationMs }
}

data class Artist(
    val name: String,
    val tracks: List<Track>,
) {
    val albumCount: Int get() = tracks.map { it.album }.distinct().size
}

data class Folder(
    val path: String,
    val name: String,
    val tracks: List<Track>,
) {
    val durationMs: Long get() = tracks.sumOf { it.durationMs }
}

/** Eine Playlist ist nur eine Liste von Titel-IDs, damit sie Media-Rescans überlebt. */
data class Playlist(
    val name: String,
    val trackIds: List<Long>,
)
