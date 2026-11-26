package com.streamplay.music.ui.app

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.streamplay.music.AppContainer
import com.streamplay.music.StreamPlayApplication

/** Resolves the application container inside a `viewModelFactory { initializer { … } }` block. */
val CreationExtras.container: AppContainer
    get() = (checkNotNull(this[APPLICATION_KEY]) as StreamPlayApplication).container
