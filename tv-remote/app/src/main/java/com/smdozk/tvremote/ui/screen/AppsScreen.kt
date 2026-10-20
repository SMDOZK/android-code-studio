package com.smdozk.tvremote.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smdozk.tvremote.model.AppInfo
import com.smdozk.tvremote.ui.theme.CardColor
import com.smdozk.tvremote.ui.theme.Cyan
import com.smdozk.tvremote.ui.theme.ErrorColor
import com.smdozk.tvremote.viewmodel.MainViewModel

@Composable
fun AppsScreen(vm: MainViewModel) {
    val apps by vm.apps.collectAsState()
    val isLoading by vm.isLoadingApps.collectAsState()
    val thirdPartyOnly by vm.appsFilter.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var uninstallTarget by remember { mutableStateOf<AppInfo?>(null) }

    val filtered = apps.filter {
        searchQuery.isBlank() || it.packageName.contains(searchQuery, ignoreCase = true)
    }

    LaunchedEffect(Unit) {
        if (apps.isEmpty()) vm.loadApps()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar
        Surface(color = CardColor) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filtered.size} uygulama",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sadece 3. taraf", fontSize = 12.sp, color = Color.Gray)
                        Spacer(Modifier.width(4.dp))
                        Switch(
                            checked = thirdPartyOnly,
                            onCheckedChange = { vm.loadApps(!it.not()) },
                            modifier = Modifier.height(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { vm.loadApps(thirdPartyOnly) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Yenile", tint = Cyan)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Uygulama ara...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Cyan)
                    Spacer(Modifier.height(8.dp))
                    Text("Uygulamalar yükleniyor...", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    AppItem(
                        app = app,
                        onLaunch = { vm.launchApp(app.packageName) },
                        onStop = { vm.stopApp(app.packageName) },
                        onUninstall = { uninstallTarget = app }
                    )
                }
            }
        }
    }

    uninstallTarget?.let { app ->
        AlertDialog(
            onDismissRequest = { uninstallTarget = null },
            title = { Text("Uygulamayı Kaldır") },
            text = { Text("${app.packageName} uygulaması TV'den kaldırılsın mı?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.uninstallApp(app.packageName)
                        uninstallTarget = null
                    }
                ) {
                    Text("Kaldır", color = ErrorColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { uninstallTarget = null }) { Text("İptal") }
            }
        )
    }
}

@Composable
private fun AppItem(
    app: AppInfo,
    onLaunch: () -> Unit,
    onStop: () -> Unit,
    onUninstall: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Android,
                contentDescription = null,
                tint = Cyan.copy(alpha = 0.6f),
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = app.displayName,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Text(
                    text = app.packageName,
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
            // Quick launch
            IconButton(onClick = onLaunch, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Başlat", tint = Cyan)
            }
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Daha fazla", tint = Color.Gray)
            }
        }

        if (expanded) {
            HorizontalDivider(color = com.smdozk.tvremote.ui.theme.SurfaceVariant)
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = { onLaunch(); expanded = false }) {
                    Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Başlat", fontSize = 12.sp)
                }
                TextButton(onClick = { onStop(); expanded = false }) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Durdur", fontSize = 12.sp)
                }
                TextButton(onClick = { onUninstall(); expanded = false }) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = ErrorColor)
                    Spacer(Modifier.width(4.dp))
                    Text("Kaldır", fontSize = 12.sp, color = ErrorColor)
                }
            }
        }
    }
}
