package com.smdozk.tvremote

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.smdozk.tvremote.ui.TvRemoteApp
import com.smdozk.tvremote.ui.theme.Background
import com.smdozk.tvremote.ui.theme.TvRemoteTheme
import com.smdozk.tvremote.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    // Same instance the composables get from viewModel(): both use this activity's store.
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleShare(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        requestHighestRefreshRate()
        setContent {
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

    // Many phones run apps at 60 Hz unless the window asks for a faster mode.
    private fun requestHighestRefreshRate() {
        val display = currentDisplay() ?: return
        val current = display.mode
        val fastest = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate }
            ?: return
        if (fastest.modeId == window.attributes.preferredDisplayModeId) return
        window.attributes = window.attributes.apply { preferredDisplayModeId = fastest.modeId }
    }

    private fun currentDisplay(): Display? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display
        else @Suppress("DEPRECATION") windowManager.defaultDisplay
}
