package com.smdozk.tvremote

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.smdozk.tvremote.ui.TvRemoteApp
import com.smdozk.tvremote.ui.theme.Background
import com.smdozk.tvremote.ui.theme.ThemeState
import com.smdozk.tvremote.ui.theme.TvRemoteTheme
import com.smdozk.tvremote.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    // Same instance the composables get from viewModel(): both use this activity's store.
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleShare(intent)
        ThemeState.light = vm.lightTheme
        applySystemBars(ThemeState.light)
        requestHighestRefreshRate()
        setContent {
            val light = ThemeState.light
            LaunchedEffect(light) { applySystemBars(light) }
            TvRemoteTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Background) {
                    TvRemoteApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    // "Share → TV Remote" from YouTube or the browser opens the link on the TV.
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val url = Regex("https?://\\S+").find(text)?.value ?: return
        vm.openOnTv(url)
    }

    // Transparent bars; their icons turn dark on the light theme so the clock stays readable.
    private fun applySystemBars(light: Boolean) {
        val style = if (light) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT) else SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    // Many phones run apps at 60 Hz unless the window asks for a faster mode.
    private fun requestHighestRefreshRate() {
        val display = ContextCompat.getDisplayOrDefault(this)
        val current = display.mode
        val fastest = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate }
            ?: return
        if (fastest.modeId == window.attributes.preferredDisplayModeId) return
        window.attributes = window.attributes.apply { preferredDisplayModeId = fastest.modeId }
    }
}
