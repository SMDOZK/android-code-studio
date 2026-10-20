package com.smdozk.tvremote.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.smdozk.tvremote.ui.screen.AppsScreen
import com.smdozk.tvremote.ui.screen.RemoteScreen
import com.smdozk.tvremote.ui.screen.ScreenshotScreen
import com.smdozk.tvremote.ui.screen.TerminalScreen
import com.smdozk.tvremote.ui.theme.*
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
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("TV Remote", color = Cyan, fontWeight = FontWeight.Bold)
                        deviceInfo?.let {
                            Text(
                                text = "${it.displayName} · Android ${it.androidVersion}",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurfaceDim
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = vm::disconnect) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Bağlantıyı kes",
                            tint = OnSurfaceDim
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Surface,
                tonalElevation = 0.dp
            ) {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Cyan,
                            selectedTextColor = Cyan,
                            indicatorColor = CyanGlow.copy(alpha = 0.3f),
                            unselectedIconColor = OnSurfaceDim,
                            unselectedTextColor = OnSurfaceDim
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
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
