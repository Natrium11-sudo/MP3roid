package com.mp3roid.player.ui.screens

import android.app.Activity
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsStateWithLifecycleCompat
import com.mp3roid.player.R
import com.mp3roid.player.data.Album
import com.mp3roid.player.data.Artist
import com.mp3roid.player.data.Folder
import com.mp3roid.player.data.Playlist
import com.mp3roid.player.data.Track
import com.mp3roid.player.ui.MP3roidViewModel
import com.mp3roid.player.ui.components.CoverArt
import com.mp3roid.player.ui.components.EmptyState
import com.mp3roid.player.ui.components.MP3roidIconButton
import com.mp3roid.player.ui.components.RoundCover
import com.mp3roid.player.ui.components.SelectChip
import com.mp3roid.player.ui.components.SongRow
import com.mp3roid.player.ui.components.formatTime
import com.mp3roid.player.ui.components.formatTotalTime
import com.mp3roid.player.ui.theme.LocalMP3roidTheme

private enum class LibrarySection(val label: String) {
    Albums("Alben"),
    Artists("Interpreten"),
    Folders("Ordner"),
    Tracks("Titel"),
    Favorites("Favoriten"),
    Playlists("Playlists"),
}

@Composable
fun LibraryPage(
    vm: MP3roidViewModel,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenFullSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalMP3roidTheme.current
    val albums by vm.albums.collectAsStateWithLifecycleCompat()
    val artists by vm.artists.collectAsStateWithLifecycleCompat()
    val folders by vm.folders.collectAsStateWithLifecycleCompat()
    val tracks by vm.tracks.collectAsStateWithLifecycleCompat()
    val favorites by vm.favorites.collectAsStateWithLifecycleCompat()
    val playlists by vm.playlists.collectAsStateWithLifecycleCompat()
    val scanning by vm.scanning.collectAsStateWithLifecycleCompat()

    var sectionIndex by remember { mutableIntStateOf(0) }
    val section = LibrarySection.entries[sectionIndex]

    var actionTrack by remember { mutableStateOf<Track?>(null) }
    var editAlbum by remember { mutableStateOf<Album?>(null) }
    var showNewPlaylist by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when (section) {
                    LibrarySection.Albums -> "${albums.size} Alben"
                    LibrarySection.Artists -> "${artists.size} Interpreten"
                    LibrarySection.Folders -> "${folders.size} Ordner"
                    LibrarySection.Tracks -> "${tracks.size} Titel"
                    LibrarySection.Favorites -> "${favorites.size} Favoriten"
                    LibrarySection.Playlists -> "${playlists.size} Playlists"
                },
                style = MaterialTheme.typography.labelSmall,
                color = theme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            MP3roidIconButton(
                iconRes = R.drawable.ic_search,
                contentDescription = "Suchen",
                onClick = onOpenFullSearch,
                size = 40.dp,
                iconSize = 20.dp,
            )
            MP3roidIconButton(
                iconRes = R.drawable.ic_refresh,
                contentDescription = "Neu einlesen",
                onClick = vm::refreshLibrary,
                enabled = !scanning,
                size = 40.dp,
                iconSize = 20.dp,
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(LibrarySection.entries.toList()) { entry ->
                SelectChip(
                    label = entry.label,
                    selected = entry == section,
                    onClick = { sectionIndex = LibrarySection.entries.indexOf(entry) },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        when (section) {
            LibrarySection.Albums -> AlbumGrid(
                albums = albums,
                vm = vm,
                onOpenAlbum = onOpenAlbum,
                onEditAlbum = { editAlbum = it },
            )

            LibrarySection.Artists -> ArtistList(
                artists = artists,
                vm = vm,
                onOpenArtist = onOpenArtist,
            )

            LibrarySection.Folders -> FolderList(
                folders = folders,
                onOpenFolder = onOpenFolder,
            )

            LibrarySection.Tracks -> TrackList(
                tracks = tracks,
                vm = vm,
                onLongPress = { actionTrack = it },
                onPlay = { index -> vm.playTracks(tracks, index) },
            )

            LibrarySection.Favorites -> {
                val favoriteTracks = tracks.filter { favorites.contains(it.id) }
                if (favoriteTracks.isEmpty()) {
                    EmptyState(
                        iconRes = R.drawable.ic_favorite_border,
                        title = "Noch keine Favoriten",
                        hint = "Halte einen Titel gedrückt und wähle „Zu Favoriten“.",
                    )
                } else {
                    TrackList(
                        tracks = favoriteTracks,
                        vm = vm,
                        onLongPress = { actionTrack = it },
                        onPlay = { index -> vm.playTracks(favoriteTracks, index) },
                    )
                }
            }

            LibrarySection.Playlists -> Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    SelectChip(
                        label = "+ Neue Playlist",
                        selected = false,
                        onClick = { showNewPlaylist = true },
                    )
                }
                if (playlists.isEmpty()) {
                    EmptyState(
                        iconRes = R.drawable.ic_playlist_play,
                        title = "Keine Playlists",
                        hint = "Lege eine Playlist an und fülle sie über einen langen Druck auf einen Titel.",
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(playlists, key = { it.name }) { playlist ->
                            PlaylistRow(
                                playlist = playlist,
                                vm = vm,
                                onClick = { onOpenPlaylist(playlist.name) },
                            )
                        }
                    }
                }
            }
        }
    }

    DeleteConsentHandler(vm)

    actionTrack?.let { track ->
        TrackActionHost(
            vm = vm,
            track = track,
            onDismiss = { actionTrack = null },
        )
    }

    editAlbum?.let { album ->
        com.mp3roid.player.ui.components.EditAlbumDialog(
            album = album,
            artwork = vm.artwork,
            hasNameOverride = vm.isAlbumRenamed(album),
            hasCustomCover = vm.hasCustomCover(album),
            onSaveName = { vm.renameAlbum(album, it) },
            onResetName = { vm.resetAlbumName(album); editAlbum = null },
            onPickCover = { vm.setAlbumCover(album, it) },
            onClearCover = { vm.clearAlbumCover(album) },
            onDismiss = { editAlbum = null },
        )
    }

    if (showNewPlaylist) {
        com.mp3roid.player.ui.components.NewPlaylistDialog(
            onCreate = { name ->
                vm.createPlaylist(name)
                showNewPlaylist = false
            },
            onDismiss = { showNewPlaylist = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumGrid(
    albums: List<Album>,
    vm: MP3roidViewModel,
    onOpenAlbum: (Long) -> Unit,
    onEditAlbum: (Album) -> Unit,
) {
    if (albums.isEmpty()) {
        EmptyState(
            iconRes = R.drawable.ic_album,
            title = "Keine Musik gefunden",
            hint = "Kopiere MP3s auf das Handy (Ordner /Music) und tippe oben auf „Neu einlesen“.",
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            Column(
                modifier = Modifier.combinedClickable(
                    onClick = { onOpenAlbum(album.id) },
                    onLongClick = { onEditAlbum(album) },
                ),
            ) {
                CoverArt(
                    loader = vm.artwork,
                    albumId = album.id,
                    seed = album.name,
                    corner = LocalMP3roidTheme.current.colors.cardCorner,
                    iconSize = 34.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalMP3roidTheme.current.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = album.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalMP3roidTheme.current.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ArtistList(artists: List<Artist>, vm: MP3roidViewModel, onOpenArtist: (String) -> Unit) {
    if (artists.isEmpty()) {
        EmptyState(
            iconRes = R.drawable.ic_person,
            title = "Keine Interpreten gefunden",
            hint = "Sobald Musik auf dem Gerät liegt, erscheint sie hier.",
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(artists, key = { it.name }) { artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenArtist(artist.name) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundCover(
                    loader = vm.artwork,
                    seed = artist.name,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = LocalMP3roidTheme.current.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${artist.tracks.size} Titel · ${artist.albumCount} Alben",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalMP3roidTheme.current.colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderList(folders: List<Folder>, onOpenFolder: (String) -> Unit) {
    if (folders.isEmpty()) {
        EmptyState(
            iconRes = R.drawable.ic_folder,
            title = "Keine Ordner gefunden",
            hint = "Musik auf der Speicherkarte wird automatisch einsortiert.",
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(folders, key = { it.path }) { folder ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenFolder(folder.path) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_folder_open),
                    contentDescription = null,
                    tint = LocalMP3roidTheme.current.colors.accent,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = LocalMP3roidTheme.current.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${folder.tracks.size} Titel · ${formatTotalTime(folder.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalMP3roidTheme.current.colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<Track>,
    vm: MP3roidViewModel,
    onLongPress: (Track) -> Unit,
    onPlay: (Int) -> Unit,
) {
    if (tracks.isEmpty()) {
        EmptyState(
            iconRes = R.drawable.ic_music_note,
            title = "Keine Titel",
            hint = "Tippe oben auf „Neu einlesen“, wenn Musik auf dem Gerät liegt.",
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            SongRow(
                title = track.title,
                subtitle = listOf(track.artist, formatTime(track.durationMs))
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                loader = vm.artwork,
                track = track,
                onClick = { onPlay(index) },
                onLongClick = { onLongPress(track) },
            )
        }
    }
}

@Composable
private fun PlaylistRow(playlist: Playlist, vm: MP3roidViewModel, onClick: () -> Unit) {
    val theme = LocalMP3roidTheme.current
    val firstTrack = vm.tracksOf(playlist).firstOrNull()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            loader = vm.artwork,
            track = firstTrack,
            albumId = firstTrack?.albumId ?: -1L,
            seed = playlist.name,
            corner = 8.dp,
            iconSize = 18.dp,
            modifier = Modifier.size(50.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                color = theme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${playlist.trackIds.size} Titel",
                style = MaterialTheme.typography.labelSmall,
                color = theme.colors.textSecondary,
            )
        }
    }
}

/** Gemeinsamer Aufruf für das Kontextmenü eines Titels. */
/**
 * Startet den System-Zustimmungsdialog fürs Löschen nicht-eigener Dateien
 * (Android 10 RecoverableSecurityException). Muss in jedem Bildschirm stehen,
 * der [TrackActionHost] hostet.
 */
@Composable
fun DeleteConsentHandler(vm: MP3roidViewModel) {
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        vm.onDeleteConsentResult(result.resultCode == Activity.RESULT_OK)
    }
    val consent by vm.deleteConsent.collectAsStateWithLifecycleCompat()
    LaunchedEffect(consent) {
        consent?.let { sender ->
            consentLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }
}

@Composable
fun TrackActionHost(
    vm: MP3roidViewModel,
    track: Track,
    onDismiss: () -> Unit,
) {
    val playlists by vm.playlists.collectAsStateWithLifecycleCompat()
    val favorites by vm.favorites.collectAsStateWithLifecycleCompat()

    // „Titel bearbeiten“ ersetzt das Menü durch den eigenen Dialog.
    var renaming by remember(track.id) { mutableStateOf(false) }
    var deleting by remember(track.id) { mutableStateOf(false) }

    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Titel löschen?") },
            text = {
                Text("„${track.title}“ wird vom Gerät entfernt. Das kann nicht rückgängig gemacht werden.")
            },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    vm.deleteTrack(track)
                    onDismiss()
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }) { Text("Abbrechen") }
            },
        )
        return
    }

    if (renaming) {
        com.mp3roid.player.ui.components.EditTrackTitleDialog(
            track = track,
            hasTitleOverride = vm.isTrackRenamed(track),
            onSave = { title ->
                vm.renameTrack(track, title)
                onDismiss()
            },
            onReset = {
                vm.resetTrackTitle(track)
                onDismiss()
            },
            onDismiss = { renaming = false },
        )
        return
    }

    com.mp3roid.player.ui.components.TrackActionsDialog(
        track = track,
        playlists = playlists,
        favorite = favorites.contains(track.id),
        hasLocalQueue = vm.localState.collectAsStateWithLifecycleCompat().value.hasItem,
        onDismiss = onDismiss,
        onPlayNext = { vm.playNext(track) },
        onAddToQueue = { vm.addToQueue(track) },
        onToggleFavorite = { vm.toggleFavorite(track.id) },
        onAddToPlaylist = { name -> vm.addToPlaylist(name, track.id) },
        onCreatePlaylist = { },
        onRename = { renaming = true },
        onDelete = { deleting = true },
    )
}

@Composable
fun AlbumDetail(
    vm: MP3roidViewModel,
    albumId: Long,
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
) {
    val album = vm.albumById(albumId)
    var editing by remember(albumId) { mutableStateOf(false) }
    DeleteConsentHandler(vm)
    CollectionLayout(
        title = album?.name ?: "Album",
        subtitle = album?.artist.orEmpty(),
        meta = album?.let { "${it.tracks.size} Titel · ${formatTotalTime(it.durationMs)}" }.orEmpty(),
        tracks = album?.tracks.orEmpty(),
        loader = vm.artwork,
        albumId = albumId,
        onBack = onBack,
        onPlayAll = { vm.playTracks(album?.tracks.orEmpty(), 0) },
        onShuffle = { vm.playShuffled(album?.tracks.orEmpty()) },
        vm = vm,
        onTitleClick = { album?.artist?.let(onOpenArtist) },
        onEditCover = if (album != null) {
            { editing = true }
        } else {
            null
        },
    )

    if (editing && album != null) {
        com.mp3roid.player.ui.components.EditAlbumDialog(
            album = album,
            artwork = vm.artwork,
            hasNameOverride = vm.isAlbumRenamed(album),
            hasCustomCover = vm.hasCustomCover(album),
            onSaveName = { vm.renameAlbum(album, it) },
            onResetName = { vm.resetAlbumName(album); editing = false },
            onPickCover = { vm.setAlbumCover(album, it) },
            onClearCover = { vm.clearAlbumCover(album) },
            onDismiss = { editing = false },
        )
    }
}

@Composable
fun CollectionLayout(
    title: String,
    subtitle: String,
    meta: String,
    tracks: List<Track>,
    loader: com.mp3roid.player.data.ArtworkLoader,
    albumId: Long,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    vm: MP3roidViewModel,
    onTitleClick: (() -> Unit)? = null,
    onEditCover: (() -> Unit)? = null,
) {
    val theme = LocalMP3roidTheme.current
    var actionTrack by remember { mutableStateOf<Track?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MP3roidIconButton(
                iconRes = R.drawable.ic_arrow_back,
                contentDescription = "Zurück",
                onClick = onBack,
                size = 44.dp,
                iconSize = 22.dp,
            )
            Spacer(Modifier.weight(1f))
            if (tracks.isNotEmpty()) {
                MP3roidIconButton(
                    iconRes = R.drawable.ic_shuffle,
                    contentDescription = "Zufällig abspielen",
                    onClick = onShuffle,
                    size = 42.dp,
                    iconSize = 20.dp,
                )
                MP3roidIconButton(
                    iconRes = R.drawable.ic_play_arrow,
                    contentDescription = "Alles abspielen",
                    onClick = onPlayAll,
                    filled = true,
                    size = 42.dp,
                    iconSize = 22.dp,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverArt(
                loader = loader,
                albumId = albumId,
                seed = title,
                corner = theme.colors.cardCorner,
                iconSize = 30.dp,
                modifier = Modifier
                    .size(108.dp)
                    .then(
                        // Langes Drücken auf das große Cover öffnet den
                        // Bearbeiten-Dialog; tippen selbst bleibt ohne Aktion.
                        if (onEditCover != null) {
                            val editCover = onEditCover
                            Modifier.pointerInput(albumId) {
                                detectTapGestures(onLongPress = { editCover() })
                            }
                        } else {
                            Modifier
                        },
                    ),
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = if (onTitleClick != null) {
                    Modifier.clickable(onClick = onTitleClick)
                } else {
                    Modifier
                },
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = theme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.colors.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.colors.textSecondary,
                    )
                }
            }
        }

        if (tracks.isEmpty()) {
            EmptyState(
                iconRes = R.drawable.ic_music_note,
                title = "Nichts drin",
                hint = "In dieser Liste liegen keine Titel.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                    SongRow(
                        title = track.title,
                        subtitle = listOf(track.artist, formatTime(track.durationMs))
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        loader = loader,
                        track = track,
                        onClick = { vm.playTracks(tracks, index) },
                        onLongClick = { actionTrack = track },
                    )
                }
            }
        }
    }

    actionTrack?.let { track ->
        TrackActionHost(vm = vm, track = track, onDismiss = { actionTrack = null })
    }
}

@Composable
fun ArtistDetail(vm: MP3roidViewModel, name: String, onBack: () -> Unit) {
    val artist = vm.artistByName(name)
    val tracks = artist?.tracks.orEmpty()
    val theme = LocalMP3roidTheme.current
    DeleteConsentHandler(vm)
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MP3roidIconButton(
                iconRes = R.drawable.ic_arrow_back,
                contentDescription = "Zurück",
                onClick = onBack,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                color = theme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (tracks.isEmpty()) {
            EmptyState(
                iconRes = R.drawable.ic_person,
                title = "Keine Titel",
                hint = "Von diesem Interpreten liegt nichts auf dem Gerät.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                    SongRow(
                        title = track.title,
                        subtitle = track.album,
                        loader = vm.artwork,
                        track = track,
                        onClick = { vm.playTracks(tracks, index) },
                    )
                }
            }
        }
    }
}

@Composable
fun FolderDetail(vm: MP3roidViewModel, path: String, onBack: () -> Unit) {
    val folder = vm.folderByPath(path)
    CollectionLayout(
        title = folder?.name ?: path.substringAfterLast('/'),
        subtitle = "",
        meta = folder?.let { "${it.tracks.size} Titel · ${formatTotalTime(it.durationMs)}" }.orEmpty(),
        tracks = folder?.tracks.orEmpty(),
        loader = vm.artwork,
        albumId = folder?.tracks?.firstOrNull()?.albumId ?: -1L,
        onBack = onBack,
        onPlayAll = { vm.playTracks(folder?.tracks.orEmpty(), 0) },
        onShuffle = { vm.playShuffled(folder?.tracks.orEmpty()) },
        vm = vm,
    )
}

@Composable
fun PlaylistDetail(vm: MP3roidViewModel, name: String, onBack: () -> Unit) {
    val playlist = vm.playlistByName(name)
    val tracks = playlist?.let { vm.tracksOf(it) }.orEmpty()
    CollectionLayout(
        title = name,
        subtitle = "Playlist",
        meta = "${tracks.size} Titel",
        tracks = tracks,
        loader = vm.artwork,
        albumId = tracks.firstOrNull()?.albumId ?: -1L,
        onBack = onBack,
        onPlayAll = { vm.playTracks(tracks, 0) },
        onShuffle = { vm.playShuffled(tracks) },
        vm = vm,
    )
}

@Composable
fun FavoritesDetail(vm: MP3roidViewModel, onBack: () -> Unit) {
    val tracks = vm.favoriteTracks()
    CollectionLayout(
        title = "Favoriten",
        subtitle = "",
        meta = "${tracks.size} Titel",
        tracks = tracks,
        loader = vm.artwork,
        albumId = tracks.firstOrNull()?.albumId ?: -1L,
        onBack = onBack,
        onPlayAll = { vm.playTracks(tracks, 0) },
        onShuffle = { vm.playShuffled(tracks) },
        vm = vm,
    )
}

@Composable
fun QueueScreen(vm: MP3roidViewModel, onBack: () -> Unit) {
    val state by vm.localState.collectAsStateWithLifecycleCompat()
    val theme = LocalMP3roidTheme.current
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MP3roidIconButton(
                iconRes = R.drawable.ic_arrow_back,
                contentDescription = "Zurück",
                onClick = onBack,
            )
            Spacer(Modifier.width(6.dp))
            Column {
                Text(
                    text = "Warteschlange",
                    style = MaterialTheme.typography.titleLarge,
                    color = theme.colors.textPrimary,
                )
                Text(
                    text = "${state.queueIndex + 1} von ${state.queueSize}",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.colors.textSecondary,
                )
            }
        }
        if (state.upNext.isEmpty()) {
            EmptyState(
                iconRes = R.drawable.ic_queue_music,
                title = "Warteschlange ist leer",
                hint = "Starte eine Liste oder ein Album – hier siehst du, was als Nächstes kommt.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                itemsIndexed(state.upNext) { _, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "▸",
                            color = theme.colors.accent,
                            modifier = Modifier.width(18.dp),
                        )
                        Column {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = theme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (item.artist.isNotBlank()) {
                                Text(
                                    text = item.artist,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = theme.colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(vm: MP3roidViewModel, onBack: () -> Unit, onOpenApp: (com.mp3roid.player.data.AppEntry) -> Unit) {
    val theme = LocalMP3roidTheme.current
    val tracks by vm.tracks.collectAsStateWithLifecycleCompat()
    val apps by vm.apps.collectAsStateWithLifecycleCompat()
    var query by remember { mutableStateOf("") }
    var actionTrack by remember { mutableStateOf<Track?>(null) }

    val needle = query.trim().lowercase()
    val matchedTracks = remember(needle, tracks) {
        if (needle.isEmpty()) {
            emptyList()
        } else {
            tracks.filter {
                it.title.lowercase().contains(needle) ||
                    it.artist.lowercase().contains(needle) ||
                    it.album.lowercase().contains(needle)
            }.take(60)
        }
    }
    val matchedApps = remember(needle, apps) {
        if (needle.isEmpty()) {
            emptyList()
        } else {
            apps.filter { it.label.lowercase().contains(needle) }.take(40)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MP3roidIconButton(
                iconRes = R.drawable.ic_arrow_back,
                contentDescription = "Zurück",
                onClick = onBack,
            )
            androidx.compose.material3.OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Titel, Interpret, Album, App …") },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
        ) {
            if (matchedTracks.isNotEmpty()) {
                item { com.mp3roid.player.ui.components.SectionHeader(title = "Titel") }
                itemsIndexed(matchedTracks, key = { _, track -> track.id }) { index, track ->
                    SongRow(
                        title = track.title,
                        subtitle = listOf(track.artist, track.album)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        loader = vm.artwork,
                        track = track,
                        onClick = { vm.playTracks(matchedTracks, index) },
                        onLongClick = { actionTrack = track },
                    )
                }
            }
            if (matchedApps.isNotEmpty()) {
                item { com.mp3roid.player.ui.components.SectionHeader(title = "Apps") }
                items(matchedApps, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenApp(app) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app = app, size = 42.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = theme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (needle.isNotEmpty() && matchedTracks.isEmpty() && matchedApps.isEmpty()) {
                item {
                    Text(
                        text = "Nichts gefunden",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                    )
                }
            }
        }
    }

    actionTrack?.let { track ->
        TrackActionHost(vm = vm, track = track, onDismiss = { actionTrack = null })
    }
}
