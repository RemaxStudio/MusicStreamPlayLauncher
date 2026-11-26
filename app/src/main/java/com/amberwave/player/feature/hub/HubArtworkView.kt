package com.amberwave.player.feature.hub

import android.content.Context
import android.util.AttributeSet
import com.amberwave.player.core.ui.CoverArtView

/** 保留首页 XML 的稳定类名，实际能力由跨页面的 [CoverArtView] 提供。 */
class HubArtworkView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : CoverArtView(context, attrs)
