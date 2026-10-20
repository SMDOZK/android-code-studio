package com.smdozk.tvremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.smdozk.tvremote.ui.TvRemoteApp
import com.smdozk.tvremote.ui.theme.TvRemoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TvRemoteTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TvRemoteApp()
                }
            }
        }
    }
}
