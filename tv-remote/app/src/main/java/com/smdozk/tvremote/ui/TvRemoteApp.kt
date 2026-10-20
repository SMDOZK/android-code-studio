package com.smdozk.tvremote.ui

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

    when (state) {
        ConnectionState.CONNECTED -> MainScreen(vm)
        else -> ConnectScreen(vm)
    }
}
