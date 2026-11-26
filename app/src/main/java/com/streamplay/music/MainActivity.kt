package com.streamplay.music

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.streamplay.music.ui.app.StreamPlayApp
import com.streamplay.music.ui.theme.StreamPlayTheme

/**
 * Single activity hosting the Compose UI. It extends AppCompatActivity (a ComponentActivity) only so
 * that per-app language changes are applied on Android 12 and lower.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // The media controller follows the activity: connected while started, released when stopped.
        lifecycle.addObserver((application as StreamPlayApplication).container.playback)
        setContent {
            StreamPlayTheme {
                StreamPlayApp()
            }
        }
    }
}
