package com.smdozk.tvremote.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smdozk.tvremote.model.ConnectionState
import com.smdozk.tvremote.ui.screen.ConnectScreen
import com.smdozk.tvremote.viewmodel.MainViewModel

@Composable
fun TvRemoteApp(vm: MainViewModel = viewModel()) {
    val state by vm.connectionState.collectAsState()

    AnimatedContent(
        targetState = state == ConnectionState.CONNECTED,
        transitionSpec = {
            (fadeIn(tween(320)) + scaleIn(tween(320), initialScale = 0.96f)) togetherWith fadeOut(tween(160))
        },
        label = "root"
    ) { connected ->
        if (connected) MainScreen(vm) else ConnectScreen(vm)
    }
}
