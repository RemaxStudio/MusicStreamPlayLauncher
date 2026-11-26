package com.streamplay.music.core.model

import androidx.annotation.StringRes
import com.streamplay.music.R

/**
 * Stable failure semantics published by ViewModels. The UI resolves text through resources so
 * raw SDK exception messages never reach the user and switching language re-translates them.
 */
enum class LoadIssue(
    @param:StringRes val titleRes: Int,
    @param:StringRes val detailRes: Int?,
) {
    HOME(R.string.load_error_home_title, R.string.load_error_connection_detail),
    RECOMMENDED(R.string.load_error_recommended_title, R.string.load_error_connection_detail),
    RECOMMENDED_PAGE(R.string.load_error_recommended_more, null),
    SEARCH(R.string.load_error_search_title, R.string.load_error_connection_detail),
    SEARCH_PAGE(R.string.load_error_search_more, null),
    DEVICE_LIBRARY(R.string.local_music_error_title, R.string.load_error_local_music),
    ARTIST(R.string.artist_load_failed, null),
    ARTIST_PAGE(R.string.artist_more_songs_load_failed, null),
}
