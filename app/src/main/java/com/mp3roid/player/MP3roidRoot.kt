package com.mp3roid.player

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mp3roid.player.launcher.SystemBarsController
import com.mp3roid.player.ui.MP3roidViewModel
import com.mp3roid.player.ui.WallpaperState
import com.mp3roid.player.ui.components.MP3roidIconButton
import com.mp3roid.player.ui.components.MiniPlayer
import com.mp3roid.player.ui.screens.AlbumDetail
import com.mp3roid.player.ui.screens.AppsPage
import com.mp3roid.player.ui.screens.ArtistDetail
import com.mp3roid.player.ui.screens.EqualizerScreen
import com.mp3roid.player.ui.screens.FavoritesDetail
import com.mp3roid.player.ui.screens.FolderDetail
import com.mp3roid.player.ui.screens.LibraryPage
import com.mp3roid.player.ui.screens.OnboardingScreen
import com.mp3roid.player.ui.screens.PlaylistDetail
import com.mp3roid.player.ui.screens.PlayerPage
import com.mp3roid.player.ui.screens.PlayerSettingsScreen
import com.mp3roid.player.ui.screens.QueueScreen
import com.mp3roid.player.ui.screens.SearchScreen
import com.mp3roid.player.ui.screens.SettingsScreen
import com.mp3roid.player.ui.screens.ThemeEditorScreen
import com.mp3roid.player.ui.theme.LocalMP3roidTheme
import com.mp3roid.player.ui.theme.mp3roidTypography
import com.mp3roid.player.ui.theme.toColorScheme
import kotlinx.coroutines.launch

object Routes {
    const val Home = "home"
    const val Onboarding = "onboarding"
    const val Settings = "settings"
    const val PlayerSettings = "player-settings"
    const val Equalizer = "equalizer"
    const val ThemeEditor = "theme-editor"
    const val Search = "search"
    const val Queue = "queue"
    const val Favorites = "favorites"

    const val AlbumPattern = "album/{albumId}"
    const val ArtistPattern = "artist/{name}"
    const val FolderPattern = "folder/{path}"
    const val PlaylistPattern = "playlist/{name}"

    fun album(id: Long) = "album/$id"
    fun artist(name: String) = "artist/${Uri.encode(name)}"
    fun folder(path: String) = "folder/${Uri.encode(path)}"
    fun playlist(name: String) = "playlist/${Uri.encode(name)}"
}

@Composable
fun MP3roidRoot(systemBars: SystemBarsController) {
    val vm: MP3roidViewModel = viewModel(factory = MP3roidViewModel.Factory)
    val theme by vm.theme.collectAsStateWithLifecycle()
    val showSystemBars by vm.showSystemBars.collectAsStateWithLifecycle()
    val wallpaper by vm.wallpaper.collectAsStateWithLifecycle()
    val wallpaperDim by vm.wallpaperDim.collectAsStateWithLifecycle()

    LaunchedEffect(showSystemBars) {
        systemBars.setHidden(!showSystemBars)
    }
    LaunchedEffect(Unit) {
        vm.refreshPermissions()
        vm.refreshWallpaper()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refreshPermissions()
                // Das Bild kann in Androids Tapeten-Auswahl gewechselt haben.
                vm.refreshWallpaper()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalMP3roidTheme provides theme,
    ) {
        MaterialTheme(
            colorScheme = theme.toColorScheme(),
            typography = mp3roidTypography(),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Grundfläche: Verlauf des Themes. Er liegt immer unten und
                // deckt auch den Notch-Bereich ab – so bleibt die Fläche
                // gefüllt, wenn kein Bild da ist oder das Lesen scheitert.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(theme.colors.surface, theme.colors.background),
                            ),
                        ),
                )

                // Optional das Hintergrundbild des Systems, darüber die
                // gewählte Abdunkelung. Beides zeichnet randlos bis in den
                // Notch-Bereich; abgedunkelt wird mit Schwarz, weil das die
                // Farben des Bildes nicht verfälscht.
                val wallpaperImage = (wallpaper as? WallpaperState.Ready)?.image
                if (wallpaperImage != null) {
                    Image(
                        bitmap = wallpaperImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        // Genauso wie Android das Bild selbst zeichnet: Der
                        // Versatz des Systems steht auf 0.0 / 0.5, das Bild also
                        // am linken Rand und waagerecht in der Mitte beschnitten –
                        // das ist Bias -1 (links) und 0 (Mitte). BiasAbsolute-
                        // Alignment statt CenterStart, weil letzteres bei
                        // umgestellter Schreibrichtung nach rechts spiegeln
                        // würde, Androids Versatz aber immer der linke Rand ist.
                        alignment = BiasAbsoluteAlignment(-1f, 0f),
                        modifier = Modifier.matchParentSize(),
                    )
                    if (wallpaperDim > 0) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = wallpaperDim / 100f)),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Nur wenn die Systemleisten eingeblendet sind, weicht
                        // der Inhalt ihnen aus.
                        .windowInsetsPadding(
                            if (showSystemBars) {
                                WindowInsets.systemBars
                            } else {
                                WindowInsets(0, 0, 0, 0)
                            },
                        ),
                ) {
                    MP3roidNavigation(vm)
                }
            }
        }
    }
}

@Composable
private fun MP3roidNavigation(vm: MP3roidViewModel) {
    val context = LocalContext.current
    val onboarded by vm.onboarded.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val nav = rememberNavController()

    LaunchedEffect(message) {
        val text = message
        if (text != null) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }

    if (onboarded == null) {
        Box(Modifier.fillMaxSize())
        return
    }

    // Der Startpunkt wird genau einmal festgelegt (sobald der Wert bekannt ist).
    // Sonst würde das NavHost-Diagramm nach dem Onboarding neu aufgebaut und
    // der Nutzer landete je nach Zeitpunkt doppelt auf dem Startbildschirm.
    val startDestination = remember(onboarded != null) {
        if (onboarded == true) Routes.Home else Routes.Onboarding
    }

    NavHost(navController = nav, startDestination = startDestination) {
        composable(Routes.Home) {
            HomeScaffold(vm = vm, nav = nav)
        }
        composable(Routes.Onboarding) {
            OnboardingScreen(vm = vm) {
                vm.completeOnboarding()
                nav.navigate(Routes.Home) {
                    popUpTo(Routes.Onboarding) { inclusive = true }
                }
            }
        }
        composable(Routes.Settings) {
            SettingsScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpenThemeEditor = { nav.navigate(Routes.ThemeEditor) },
            )
        }
        composable(Routes.ThemeEditor) {
            ThemeEditorScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable(Routes.PlayerSettings) {
            PlayerSettingsScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpenEqualizer = { nav.navigate(Routes.Equalizer) },
            )
        }
        composable(Routes.Equalizer) {
            EqualizerScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable(Routes.Search) {
            SearchScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpenApp = { app ->
                    vm.launchApp(app)
                    nav.popBackStack()
                },
            )
        }
        composable(Routes.Queue) {
            QueueScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable(Routes.Favorites) {
            FavoritesDetail(vm = vm, onBack = { nav.popBackStack() })
        }
        composable(
            route = Routes.AlbumPattern,
            arguments = listOf(navArgument("albumId") { type = NavType.LongType }),
        ) { entry ->
            AlbumDetail(
                vm = vm,
                albumId = entry.arguments?.getLong("albumId") ?: -1L,
                onBack = { nav.popBackStack() },
                onOpenArtist = { artist -> nav.navigate(Routes.artist(artist)) },
            )
        }
        composable(
            route = Routes.ArtistPattern,
            arguments = listOf(navArgument("name") { type = NavType.StringType }),
        ) { entry ->
            ArtistDetail(
                vm = vm,
                name = entry.arguments?.getString("name").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            route = Routes.FolderPattern,
            arguments = listOf(navArgument("path") { type = NavType.StringType }),
        ) { entry ->
            FolderDetail(
                vm = vm,
                path = entry.arguments?.getString("path").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            route = Routes.PlaylistPattern,
            arguments = listOf(navArgument("name") { type = NavType.StringType }),
        ) { entry ->
            PlaylistDetail(
                vm = vm,
                name = entry.arguments?.getString("name").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
    }
}

@Composable
private fun HomeScaffold(vm: MP3roidViewModel, nav: NavController) {
    val theme = LocalMP3roidTheme.current
    val now by vm.nowPlaying.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val localState by vm.localState.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState { PAGE_COUNT }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = true) {
        if (pagerState.currentPage != 0) {
            scope.launch { pagerState.animateScrollToPage(0) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 6.dp, top = 10.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_mp3roid_logo),
                contentDescription = "MP3roid",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "MP3roid",
                    style = MaterialTheme.typography.titleLarge,
                    color = theme.colors.textPrimary,
                )
                val subtitle = when {
                    now == null -> "Bereit"
                    now?.isLocal == true -> "Lokale Wiedergabe"
                    else -> "Wiedergabe von ${now?.sourceLabel}"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.colors.textSecondary,
                    maxLines = 1,
                )
            }
            MP3roidIconButton(
                iconRes = R.drawable.ic_search,
                contentDescription = "Suchen",
                onClick = { nav.navigate(Routes.Search) },
                size = 42.dp,
                iconSize = 21.dp,
            )
            MP3roidIconButton(
                iconRes = R.drawable.ic_settings,
                contentDescription = "Einstellungen",
                onClick = { nav.navigate(Routes.Settings) },
                size = 42.dp,
                iconSize = 21.dp,
            )
        }

        // Beim Wechsel auf die App-Seite die Liste auffrischen: so steht sie
        // auch dann aktuell da, wenn im Hintergrund installiert wurde.
        LaunchedEffect(pagerState.settledPage) {
            if (pagerState.settledPage == PAGE_COUNT - 1) vm.syncApps()
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            when (page) {
                0 -> PlayerPage(
                    vm = vm,
                    now = now,
                    sources = sources,
                    upNext = localState.upNext,
                    onOpenLibrary = {
                        scope.launch { pagerState.animateScrollToPage(1) }
                    },
                    onOpenQueue = { nav.navigate(Routes.Queue) },
                    onOpenEqualizer = { nav.navigate(Routes.Equalizer) },
                    onOpenPlayerSettings = { nav.navigate(Routes.PlayerSettings) },
                    modifier = Modifier.fillMaxSize(),
                )

                1 -> LibraryPage(
                    vm = vm,
                    onOpenAlbum = { id -> nav.navigate(Routes.album(id)) },
                    onOpenArtist = { name -> nav.navigate(Routes.artist(name)) },
                    onOpenFolder = { path -> nav.navigate(Routes.folder(path)) },
                    onOpenPlaylist = { name -> nav.navigate(Routes.playlist(name)) },
                    onOpenFullSearch = { nav.navigate(Routes.Search) },
                    modifier = Modifier.fillMaxSize(),
                )

                else -> AppsPage(
                    vm = vm,
                    onOpenSearch = { nav.navigate(Routes.Search) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        val playing = now
        // Auf der Player-Seite laeuft die volle Ansicht - der kleine Player
        // waere dort doppelt. settledPage statt currentPage, damit er nicht
        // mitten im Wischen wegspringt.
        AnimatedVisibility(
            visible = playing != null && pagerState.settledPage != 0,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            playing?.let { current ->
                MiniPlayer(
                    now = current,
                    loader = vm.artwork,
                    onOpen = { scope.launch { pagerState.animateScrollToPage(0) } },
                    onToggle = vm::togglePlayPause,
                    onNext = vm::next,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.colors.surface.copy(alpha = 0.92f))
                .padding(vertical = 4.dp),
        ) {
            BottomTab(
                iconRes = R.drawable.ic_headphones,
                label = "Player",
                selected = pagerState.currentPage == 0,
                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                modifier = Modifier.weight(1f),
            )
            BottomTab(
                iconRes = R.drawable.ic_library_music,
                label = "Bibliothek",
                selected = pagerState.currentPage == 1,
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                modifier = Modifier.weight(1f),
            )
            BottomTab(
                iconRes = R.drawable.ic_apps,
                label = "Apps",
                selected = pagerState.currentPage == 2,
                onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BottomTab(
    iconRes: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalMP3roidTheme.current
    Column(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = if (selected) theme.colors.accent else theme.colors.textSecondary,
            modifier = Modifier.size(23.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) theme.colors.accent else theme.colors.textSecondary,
        )
    }
}

private const val PAGE_COUNT = 3
