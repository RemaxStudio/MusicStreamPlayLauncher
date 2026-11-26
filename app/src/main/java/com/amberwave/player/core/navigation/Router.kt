package com.amberwave.player.core.navigation

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.amberwave.player.feature.nowplaying.PlayerTrack
import com.amberwave.player.core.ads.InterstitialPlacement
import com.amberwave.catalog.model.CatalogArtistRef
import com.amberwave.catalog.model.CatalogPlatform

/**
 * 页面间跳转的唯一入口。页面只表达“去哪里”，由宿主 Activity 映射到 Navigation 目的地，
 * 页面之间不再互相引用具体类型或拼装 Intent。
 */
interface Router {
    /** 用新的播放队列打开播放页；队列为空时忽略。 */
    fun openPlayer(queue: List<PlayerTrack>, currentTrackId: String)

    /** 从 Mini Player 打开播放页，沿用 MediaSession 中已有的队列。 */
    fun openNowPlaying()

    fun openSearch()

    fun openRecommended(placement: InterstitialPlacement)

    fun openArtist(
        platform: CatalogPlatform,
        artistId: String,
        artistName: String,
        placement: InterstitialPlacement,
    )

    fun openArtist(artist: CatalogArtistRef, placement: InterstitialPlacement) =
        openArtist(artist.platform, artist.id, artist.name, placement)

    fun openFavorites(placement: InterstitialPlacement)

    fun openPlaylist(playlistId: Long, placement: InterstitialPlacement)

    fun openLocalMusic(placement: InterstitialPlacement)

    fun openSettings()

    /** 首页形态（A/B）变化时重建首页，旧形态的依赖随旧页面一起释放。 */
    fun restartHome()

    /** 返回上一页；已在起始页时由宿主结束。 */
    fun navigateUp()
}

interface RouterHost {
    val navigator: Router
}

val FragmentActivity.navigator: Router
    get() = (this as RouterHost).navigator

val Fragment.navigator: Router
    get() = requireActivity().navigator
