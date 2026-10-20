package com.smdozk.tvremote.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smdozk.tvremote.model.DeviceInfo
import com.smdozk.tvremote.ui.components.RefreshRateBadge
import com.smdozk.tvremote.ui.screen.AppsScreen
import com.smdozk.tvremote.ui.screen.RemoteScreen
import com.smdozk.tvremote.ui.screen.ScreenshotScreen
import com.smdozk.tvremote.ui.screen.TerminalScreen
import com.smdozk.tvremote.ui.theme.Background
import com.smdozk.tvremote.ui.theme.Cyan
import com.smdozk.tvremote.ui.theme.CyanGlow
import com.smdozk.tvremote.ui.theme.OnSurface
import com.smdozk.tvremote.ui.theme.OnSurfaceDim
import com.smdozk.tvremote.ui.theme.SuccessGreen
import com.smdozk.tvremote.ui.theme.Surface
import com.smdozk.tvremote.viewmodel.MainViewModel

private data class NavItem(val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem("Kumanda", Icons.Default.SettingsRemote),
    NavItem("Uygulamalar", Icons.Default.Apps),
    NavItem("Terminal", Icons.Default.Terminal),
    NavItem("Ekran", Icons.Default.Screenshot),
)

private val TabSlideSpec = spring(
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold
)

@Composable
fun MainScreen(vm: MainViewModel) {
    val deviceInfo by vm.deviceInfo.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = Background,
        topBar = { TopBar(deviceInfo, onDisconnect = vm::disconnect) },
        bottomBar = {
            Column {
                Spacer(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))
                NavigationBar(containerColor = Surface, tonalElevation = 0.dp) {
                    navItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Cyan,
                                selectedTextColor = Cyan,
                                indicatorColor = CyanGlow.copy(alpha = 0.22f),
                                unselectedIconColor = OnSurfaceDim,
                                unselectedTextColor = OnSurfaceDim
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = selectedTab,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally(TabSlideSpec) { width -> direction * width / 6 } + fadeIn(tween(220)))
                    .togetherWith(slideOutHorizontally(TabSlideSpec) { width -> -direction * width / 6 } + fadeOut(tween(120)))
            },
            label = "tab"
        ) { tab ->
            when (tab) {
                0 -> RemoteScreen(vm)
                1 -> AppsScreen(vm)
                2 -> TerminalScreen(vm)
                else -> ScreenshotScreen(vm)
            }
        }
    }
}

@Composable
private fun TopBar(deviceInfo: DeviceInfo?, onDisconnect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(9.dp).background(SuccessGreen, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = deviceInfo?.displayName?.takeIf { it.isNotBlank() } ?: "TV Remote",
                color = OnSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = deviceInfo?.let { "Android ${it.androidVersion} · Bağlı" } ?: "Bağlı",
                color = OnSurfaceDim,
                fontSize = 12.sp
            )
        }
        RefreshRateBadge()
        IconButton(onClick = onDisconnect) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Bağlantıyı kes", tint = OnSurfaceDim)
        }
    }
}
