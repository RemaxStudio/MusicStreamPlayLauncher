package com.amberwave.player

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.amberwave.player.feature.creator.CreatorFragment
import com.amberwave.player.core.navigation.Router
import com.amberwave.player.core.navigation.RouterHost
import com.amberwave.player.core.playback.PlayerConnection
import com.amberwave.player.core.playback.PlayerConnectionHost
import com.amberwave.player.databinding.HostActivityBinding
import com.amberwave.player.feature.collection.CollectionFragment
import com.amberwave.player.feature.nowplaying.NowPlayingLaunch
import com.amberwave.player.feature.nowplaying.PlayerTrack
import com.amberwave.player.core.ads.InterstitialPlacement
import com.amberwave.player.core.ads.requestPostNavigationInterstitial
import com.amberwave.catalog.model.CatalogPlatform

/**
 * 单 Activity 宿主：承载 Navigation 导航图，持有全局唯一的播放连接，并把页面的跳转意图映射到目的地。
 */
class HostActivity : AppCompatActivity(), RouterHost, PlayerConnectionHost {
    private lateinit var binding: HostActivityBinding
    private lateinit var navController: NavController

    override val playbackConnection: PlayerConnection by lazy(LazyThreadSafetyMode.NONE) {
        PlayerConnection(this)
    }

    override val navigator: Router = object : Router {
        override fun openPlayer(queue: List<PlayerTrack>, currentTrackId: String) {
            NowPlayingLaunch.queueArgs(queue, currentTrackId)?.let { navigate(R.id.dest_player, it, playerTransition) }
        }

        override fun openNowPlaying() = navigate(R.id.dest_player, null, playerTransition)

        override fun openSearch() = navigate(R.id.dest_search)

        override fun openRecommended(placement: InterstitialPlacement) =
            navigate(R.id.dest_recommended, Bundle().requestPostNavigationInterstitial(placement))

        override fun openArtist(
            platform: CatalogPlatform,
            artistId: String,
            artistName: String,
            placement: InterstitialPlacement,
        ) {
            if (artistId.isBlank()) return
            navigate(
                R.id.dest_artist,
                CreatorFragment.args(platform, artistId, artistName).requestPostNavigationInterstitial(placement),
            )
        }

        override fun openFavorites(placement: InterstitialPlacement) =
            navigate(R.id.dest_playlist, CollectionFragment.favoritesArgs().requestPostNavigationInterstitial(placement))

        override fun openPlaylist(playlistId: Long, placement: InterstitialPlacement) =
            navigate(R.id.dest_playlist, CollectionFragment.playlistArgs(playlistId).requestPostNavigationInterstitial(placement))

        override fun openLocalMusic(placement: InterstitialPlacement) =
            navigate(R.id.dest_local_music, Bundle().requestPostNavigationInterstitial(placement))

        override fun openSettings() = navigate(R.id.dest_settings)

        override fun restartHome() {
            navController.navigate(
                R.id.dest_home,
                null,
                NavOptions.Builder().setPopUpTo(R.id.dest_home, inclusive = true).build(),
            )
        }

        override fun navigateUp() {
            if (!navController.popBackStack()) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = HostActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsControllerCompat(window, binding.root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        navController = (supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment).navController
        lifecycle.addObserver(playbackConnection)
    }

    override fun onSupportNavigateUp(): Boolean = navController.navigateUp() || super.onSupportNavigateUp()

    private fun navigate(@IdRes destination: Int, args: Bundle? = null, options: NavOptions = pageTransition) {
        // 连点或在转场中重复触发时，同一目的地不重复入栈（播放页除外：新队列需要替换当前播放页）。
        val current = navController.currentDestination?.id
        if (current == destination && destination != R.id.dest_player) return
        val navOptions = if (current == R.id.dest_player && destination == R.id.dest_player) {
            NavOptions.Builder()
                .setPopUpTo(R.id.dest_player, inclusive = true)
                .setEnterAnim(options.enterAnim)
                .setPopExitAnim(options.popExitAnim)
                .build()
        } else {
            options
        }
        navController.navigate(destination, args, navOptions)
    }

    private companion object {
        val pageTransition: NavOptions = NavOptions.Builder()
            .setEnterAnim(R.anim.nav_enter)
            .setExitAnim(R.anim.nav_exit)
            .setPopEnterAnim(R.anim.nav_pop_enter)
            .setPopExitAnim(R.anim.nav_pop_exit)
            .build()

        val playerTransition: NavOptions = NavOptions.Builder()
            .setEnterAnim(R.anim.player_enter)
            .setExitAnim(R.anim.nav_hold)
            .setPopEnterAnim(R.anim.nav_hold)
            .setPopExitAnim(R.anim.player_pop_exit)
            .build()
    }
}
