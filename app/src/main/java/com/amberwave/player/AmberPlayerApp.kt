package com.amberwave.player

import android.app.Application
import com.amberwave.player.core.ads.AdSdkBootstrap
import com.amberwave.player.core.ads.AdPolicy

/**
 * 应用唯一入口。
 *
 * Application 不再继承任何渠道 Launcher SDK 类，公共初始化因此与渠道完全解耦。
 * 广告初始化入口保留为 no-op，便于业务代码在不引入广告 SDK 的情况下继续编译。
 */
class AmberPlayerApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 本地凭据先兜底，Remote Config 完成后再热更新。
        CatalogGraph.initialize()
        RemoteConfigBootstrap.start()
        AdPolicy.initializeSession()
        AdSdkBootstrap.initialize(this)
    }
}
