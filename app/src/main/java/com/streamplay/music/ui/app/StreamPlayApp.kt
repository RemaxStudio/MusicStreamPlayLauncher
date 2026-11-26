package com.streamplay.music.ui.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.streamplay.music.StreamPlayApplication
import com.streamplay.music.core.ads.AdSlots
import com.streamplay.music.core.ads.EntryInterstitial
import com.streamplay.music.core.model.ArtistLink
import com.streamplay.music.ui.actions.SongActionsViewModel
import com.streamplay.music.ui.actions.SongOverlayHost
import com.streamplay.music.ui.artist.ArtistScreen
import com.streamplay.music.ui.collection.CollectionScreen
import com.streamplay.music.ui.components.atmosphere
import com.streamplay.music.ui.home.HomeScreen
import com.streamplay.music.ui.local.LocalMusicScreen
import com.streamplay.music.ui.navigation.ArtistDestination
import com.streamplay.music.ui.navigation.CollectionDestination
import com.streamplay.music.ui.navigation.HomeDestination
import com.streamplay.music.ui.navigation.LocalMusicDestination
import com.streamplay.music.ui.navigation.PlayerDestination
import com.streamplay.music.ui.navigation.RecommendedDestination
import com.streamplay.music.ui.navigation.SearchDestination
import com.streamplay.music.ui.navigation.SettingsDestination
import com.streamplay.music.ui.player.PlayerScreen
import com.streamplay.music.ui.recommended.RecommendedScreen
import com.streamplay.music.ui.search.SearchScreen
import com.streamplay.music.ui.settings.SettingsScreen
import com.streamplay.music.ui.theme.Palette
import com.streamplay.music.ui.theme.StreamPlay

private const val PAGE_MS = 320

/** Navigation callbacks handed to screens; screens never touch the NavController directly. */
class AppNavigator(private val nav: NavHostController) {
    fun back() {
        nav.popBackStack()
    }

    fun player() = navigateOnce(PlayerDestination)

    fun search() = navigateOnce(SearchDestination)

    fun recommended() {
        AdSlots.mayRequestInterstitial(EntryInterstitial.RECOMMENDED_MORE)
        navigateOnce(RecommendedDestination)
    }

    fun favorites() {
        AdSlots.mayRequestInterstitial(EntryInterstitial.FAVORITES)
        navigateOnce(CollectionDestination())
    }

    fun playlist(id: Long) {
        AdSlots.mayRequestInterstitial(EntryInterstitial.PLAYLIST)
        navigateOnce(CollectionDestination(id))
    }

    fun localMusic() {
        AdSlots.mayRequestInterstitial(EntryInterstitial.LOCAL_PLAYLISTS)
        navigateOnce(LocalMusicDestination)
    }

    fun settings() = navigateOnce(SettingsDestination)

    fun artist(link: ArtistLink, entry: EntryInterstitial = EntryInterstitial.ARTIST_FROM_LIST) {
        AdSlots.mayRequestInterstitial(entry)
        navigateOnce(ArtistDestination.of(link))
    }

    /** Ignore repeated taps that would stack the same destination twice during a transition. */
    private fun navigateOnce(destination: Any) {
        val current = nav.currentBackStackEntry?.destination
        val sameRoute = current?.route?.substringBefore('/')?.substringBefore('?') ==
            destination::class.qualifiedName
        val carriesArguments = destination is ArtistDestination || destination is CollectionDestination
        if (sameRoute && !carriesArguments) return
        // Destinations with arguments (another artist, another playlist) always get their own entry.
        nav.navigate(destination) { launchSingleTop = !carriesArguments }
    }
}

@Composable
fun StreamPlayApp() {
    val activity = LocalActivity.current as ComponentActivity
    val container = (LocalContext.current.applicationContext as StreamPlayApplication).container
    val playback: PlaybackViewModel = viewModel(viewModelStoreOwner = activity, factory = PlaybackViewModel.Factory)
    val songActions: SongActionsViewModel = viewModel(viewModelStoreOwner = activity, factory = SongActionsViewModel.Factory)
    val navController = rememberNavController()
    val navigator = remember(navController) { AppNavigator(navController) }
    val snackbar = remember { SnackbarHostState() }
    val session = remember(playback, songActions, navigator) { Session(playback, songActions, navigator::player, snackbar) }

    CompositionLocalProvider(LocalSession provides session) {
        Box(
            Modifier
                .fillMaxSize()
                .atmosphere(),
        ) {
            NavHost(
                navController = navController,
                startDestination = HomeDestination,
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(PAGE_MS)) + fadeIn(tween(PAGE_MS)) },
                exitTransition = { fadeOut(tween(PAGE_MS / 2)) },
                popEnterTransition = { fadeIn(tween(PAGE_MS)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(PAGE_MS)) + fadeOut(tween(PAGE_MS)) },
            ) {
                composable<HomeDestination> { HomeScreen(navigator) }
                composable<RecommendedDestination> { RecommendedScreen(navigator) }
                composable<SearchDestination> { SearchScreen(navigator) }
                composable<PlayerDestination>(
                    enterTransition = { slideInVertically(tween(PAGE_MS)) { it } + fadeIn(tween(PAGE_MS)) },
                    exitTransition = { fadeOut(tween(PAGE_MS / 2)) },
                    popEnterTransition = { fadeIn(tween(PAGE_MS)) },
                    popExitTransition = { slideOutVertically(tween(PAGE_MS)) { it } + fadeOut(tween(PAGE_MS)) },
                ) { PlayerScreen(navigator) }
                composable<ArtistDestination> { ArtistScreen(navigator) }
                composable<CollectionDestination> { CollectionScreen(navigator) }
                composable<LocalMusicDestination> { LocalMusicScreen(navigator) }
                composable<SettingsDestination> { SettingsScreen(navigator) }
            }

            SongOverlayHost(
                actions = songActions,
                playback = playback,
                remover = container.fileRemover,
                snackbar = snackbar,
                onOpenArtist = { navigator.artist(it, EntryInterstitial.ARTIST_FROM_SONG_INFO) },
            )

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 88.dp),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = StreamPlay.colors.dialog,
                    contentColor = Palette.Ink,
                    actionColor = Palette.Lilac,
                )
            }
        }
    }
}
