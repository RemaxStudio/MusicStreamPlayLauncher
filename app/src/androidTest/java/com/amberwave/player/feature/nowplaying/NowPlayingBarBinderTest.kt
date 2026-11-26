package com.amberwave.player.feature.nowplaying

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.amberwave.player.R
import com.amberwave.player.databinding.NowPlayingBarBinding
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NowPlayingBarBinderTest {
    @Test
    fun controllerReconnectEnablesExistingMiniPlayerWithoutTrackRerender() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_LocalPure)
            val binding = NowPlayingBarBinding.inflate(LayoutInflater.from(context))
            val binder = NowPlayingBarBinder(binding)

            binder.updateControllerState(controllerReady = false, hasQueue = false)
            assertFalse(binding.playPause.isEnabled)
            assertFalse(binding.queue.isEnabled)

            // 模拟从 PlayerActivity 返回后，歌曲模型未变化、只有新 Controller 连接成功。
            binder.updateControllerState(controllerReady = true, hasQueue = true)
            assertTrue(binding.playPause.isEnabled)
            assertTrue(binding.queue.isEnabled)
        }
    }
}
