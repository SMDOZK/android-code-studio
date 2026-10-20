package com.smdozk.tvremote.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.smdozk.tvremote.ui.screen.AppsScreen
import com.smdozk.tvremote.ui.screen.RemoteScreen
import com.smdozk.tvremote.ui.screen.ScreenshotScreen
import com.smdozk.tvremote.ui.screen.TerminalScreen
import com.smdozk.tvremote.ui.theme.Cyan
import com.smdozk.tvremote.ui.theme.Surface
import com.smdozk.tvremote.viewmodel.MainViewModel

private data class NavItem(val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem("Kumanda", Icons.Default.Smartphone),
    NavItem("Uygulamalar", Icons.Default.Apps),
    NavItem("Terminal", Icons.Default.Terminal),
    NavItem("Ekran", Icons.Default.Screenshot),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    val deviceInfo by vm.deviceInfo.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("TV Remote", color = Cyan)
                        deviceInfo?.let {
                            Text(
                                text = "${it.displayName} · Android ${it.androidVersion}",
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                color = androidx.compose.ui.graphics.Color.Gray
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = vm::disconnect) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Bağlantıyı kes",
                            tint = androidx.compose.ui.graphics.Color.Gray
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Surface) {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> RemoteScreen(vm)
                1 -> AppsScreen(vm)
                2 -> TerminalScreen(vm)
                3 -> ScreenshotScreen(vm)
            }
        }
    }
}
