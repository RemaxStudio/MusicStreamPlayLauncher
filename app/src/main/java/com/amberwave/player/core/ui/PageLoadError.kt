package com.amberwave.player.core.ui

import androidx.annotation.StringRes
import com.amberwave.player.R

/**
 * ViewModel 只发布稳定的加载失败语义，展示层再按当前 Locale 解析文案。
 * SDK 或系统异常的英文 message 不应直接暴露给用户，也不会在切换语言后残留。
 */
enum class PageLoadError(
    @param:StringRes val messageRes: Int,
    /** 整页状态使用“标题 + 说明”两行结构（设计稿），列表内联提示仍使用 [messageRes]。 */
    @param:StringRes val titleRes: Int? = null,
    @param:StringRes val detailRes: Int? = null,
) {
    HOME(R.string.load_error_home, R.string.load_error_home_title, R.string.load_error_connection_detail),
    RECOMMENDED(
        R.string.load_error_recommended,
        R.string.load_error_recommended_title,
        R.string.load_error_connection_detail,
    ),
    RECOMMENDED_MORE(R.string.load_error_recommended_more),
    SEARCH(R.string.load_error_search, R.string.load_error_search_title, R.string.load_error_connection_detail),
    SEARCH_MORE(R.string.load_error_search_more),
    LOCAL_MUSIC(R.string.load_error_local_music),
}
