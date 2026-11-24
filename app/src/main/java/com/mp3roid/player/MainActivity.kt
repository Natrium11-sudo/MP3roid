package com.mp3roid.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.mp3roid.player.launcher.SystemBarsController

class MainActivity : ComponentActivity() {

    private lateinit var systemBars: SystemBarsController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        systemBars = SystemBarsController(this)
        setContent {
            MP3roidRoot(systemBars)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        systemBars.onWindowFocusChanged(hasFocus)
    }
}
