package com.smdozk.tvremote.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Link
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smdozk.tvremote.model.AppInfo
import com.smdozk.tvremote.model.TvKind
import com.smdozk.tvremote.ui.components.GlowButton
import com.smdozk.tvremote.ui.components.NoticeBanner
import com.smdozk.tvremote.ui.components.TransferCard
import com.smdozk.tvremote.ui.theme.*
import com.smdozk.tvremote.ui.theme.Ink
import com.smdozk.tvremote.ui.theme.RowBrush
import com.smdozk.tvremote.viewmodel.MainViewModel

// Some file managers report APKs as a generic binary type.
private val APK_MIME_TYPES = arrayOf("application/vnd.android.package-archive", "application/octet-stream")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppsScreen(vm: MainViewModel) {
    val apps by vm.apps.collectAsState()
    val isLoading by vm.isLoadingApps.collectAsState()
    val thirdPartyOnly by vm.appsFilter.collectAsState()
    val install by vm.install.collectAsState()
    val installNotice by vm.installMessage.collectAsState()
    val backend by vm.backend.collectAsState()
    val isAdb = backend == TvKind.Adb

    var searchQuery by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    val openLink = {
        if (link.isNotBlank()) {
            vm.openOnTv(link)
            link = ""
        }
    }
    var uninstallTarget by remember { mutableStateOf<AppInfo?>(null) }
    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.installApk(uri)
    }

    val filtered = apps.filter {
        searchQuery.isBlank() ||
            it.packageName.contains(searchQuery, ignoreCase = true) ||
            it.displayName.contains(searchQuery, ignoreCase = true)
    }

    LaunchedEffect(Unit) { if (apps.isEmpty()) vm.loadApps() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(
            modifier = Modifier
                .background(Surface)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${filtered.size} uygulama", fontSize = 13.sp, color = OnSurfaceDim)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("3. taraf", fontSize = 12.sp, color = OnSurfaceDim)
                    Spacer(Modifier.width(4.dp))
                    Switch(
                        checked = thirdPartyOnly,
                        onCheckedChange = { vm.loadApps(it) },
                        modifier = Modifier.height(24.dp),
                        colors = SwitchDefaults.colors(checkedThumbColor = Cyan, checkedTrackColor = CyanGlow)
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
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = OnSurfaceDim) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null, tint = OnSurfaceDim)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan,
                    unfocusedBorderColor = SurfaceVariant
                )
            )
            Spacer(Modifier.height(8.dp))
            // YouTube links open in the TV's YouTube app, everything else in its browser.
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                placeholder = { Text("Bağlantıyı TV'de aç (YouTube, web)") },
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = OnSurfaceDim) },
                trailingIcon = {
                    IconButton(onClick = openLink, enabled = link.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "TV'de aç", tint = Cyan)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { openLink() }),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan,
                    unfocusedBorderColor = SurfaceVariant
                )
            )
            if (!isAdb) {
                Spacer(Modifier.height(10.dp))
                DeveloperCard(vm)
            }
            // Sideloading needs ADB; webOS only lets a remote launch and close apps.
            if (isAdb) {
                Spacer(Modifier.height(10.dp))
                GlowButton(
                    text = "Telefondan APK yükle",
                    onClick = { apkPicker.launch(APK_MIME_TYPES) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.InstallMobile,
                    loading = install != null
                )
                install?.let {
                    Spacer(Modifier.height(10.dp))
                    TransferCard(it)
                }
                NoticeBanner(installNotice, onDismiss = vm::clearInstallMessage, modifier = Modifier.padding(top = 10.dp))
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Cyan, strokeWidth = 2.dp)
                    Spacer(Modifier.height(10.dp))
                    Text("Uygulamalar yükleniyor...", color = OnSurfaceDim)
                }
            }
        } else if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Apps, contentDescription = null, tint = OnSurfaceDim, modifier = Modifier.size(44.dp))
                    Text(
                        if (searchQuery.isBlank()) "Uygulama bulunamadı" else "\"$searchQuery\" ile eşleşen uygulama yok",
                        color = OnSurfaceDim,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    AppItem(
                        app = app,
                        onLaunch = { vm.launchApp(app.packageName) },
                        onStop = { vm.stopApp(app.packageName) },
                        onUninstall = if (isAdb) ({ uninstallTarget = app }) else null,
                        modifier = Modifier.animateItemPlacement()
                    )
                }
            }
        }
    }

    uninstallTarget?.let { app ->
        AlertDialog(
            onDismissRequest = { uninstallTarget = null },
            containerColor = CardColor,
            title = { Text("Uygulamayı Kaldır", color = OnSurface) },
            text = { Text("${app.packageName} kaldırılsın mı?", color = OnSurfaceDim) },
            confirmButton = {
                TextButton(onClick = { vm.uninstallApp(app.packageName); uninstallTarget = null }) {
                    Text("Kaldır", color = ErrorColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { uninstallTarget = null }) { Text("İptal", color = Cyan) }
            }
        )
    }
}

@Composable
private fun AppItem(
    app: AppInfo,
    onLaunch: () -> Unit,
    onStop: () -> Unit,
    onUninstall: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RowBrush)
            .border(0.8.dp, Ink(0.05f), RoundedCornerShape(14.dp))
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CyanGlow.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    app.displayName.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
                    color = Cyan, fontWeight = FontWeight.Bold, fontSize = 16.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.displayName, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = OnSurface)
                Text(app.packageName, fontSize = 10.sp, color = OnSurfaceDim)
            }
            IconButton(onClick = onLaunch, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Başlat", tint = Cyan, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Daha fazla", tint = OnSurfaceDim, modifier = Modifier.size(20.dp))
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                HorizontalDivider(color = SurfaceVariant, thickness = 0.6.dp)
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = { onLaunch(); expanded = false }) {
                        Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = null, modifier = Modifier.size(15.dp), tint = Cyan)
                        Spacer(Modifier.width(4.dp))
                        Text("Başlat", fontSize = 12.sp, color = Cyan)
                    }
                    TextButton(onClick = { onStop(); expanded = false }) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(15.dp), tint = OnSurfaceDim)
                        Spacer(Modifier.width(4.dp))
                        Text("Durdur", fontSize = 12.sp, color = OnSurfaceDim)
                    }
                    if (onUninstall != null) {
                        TextButton(onClick = { onUninstall(); expanded = false }) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp), tint = ErrorColor)
                            Spacer(Modifier.width(4.dp))
                            Text("Kaldır", fontSize = 12.sp, color = ErrorColor)
                        }
                    }
                }
            }
        }
    }
}
