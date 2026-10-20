package com.smdozk.tvremote.ui.screen

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smdozk.tvremote.model.RemoteFile
import com.smdozk.tvremote.ui.components.AnimatedKey
import com.smdozk.tvremote.ui.components.GlowButton
import com.smdozk.tvremote.ui.components.NoticeBanner
import com.smdozk.tvremote.ui.components.TransferCard
import com.smdozk.tvremote.ui.formatDate
import com.smdozk.tvremote.ui.formatSize
import com.smdozk.tvremote.ui.theme.Amber
import com.smdozk.tvremote.ui.theme.CardColor
import com.smdozk.tvremote.ui.theme.Cyan
import com.smdozk.tvremote.ui.theme.ErrorColor
import com.smdozk.tvremote.ui.theme.GlassBorderBrush
import com.smdozk.tvremote.ui.theme.GlassBrush
import com.smdozk.tvremote.ui.theme.OnSurface
import com.smdozk.tvremote.ui.theme.OnSurfaceDim
import com.smdozk.tvremote.ui.theme.Purple
import com.smdozk.tvremote.ui.theme.SuccessGreen
import com.smdozk.tvremote.viewmodel.FilesController
import com.smdozk.tvremote.viewmodel.MainViewModel

private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")
private val VIDEO_EXT = setOf("mp4", "mkv", "avi", "mov", "webm", "ts", "m4v", "3gp")
private val AUDIO_EXT = setOf("mp3", "flac", "aac", "ogg", "wav", "m4a", "opus")
private val ARCHIVE_EXT = setOf("zip", "rar", "7z", "tar", "gz")

private val shortcuts = listOf(
    "Dahili" to FilesController.HOME,
    "İndirilenler" to "/sdcard/Download",
    "Filmler" to "/sdcard/Movies",
    "Resimler" to "/sdcard/Pictures",
    "USB / Depolama" to "/storage",
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(vm: MainViewModel) {
    val files = vm.files
    val path by files.path.collectAsState()
    val entries by files.entries.collectAsState()
    val isLoading by files.isLoading.collectAsState()
    val transfer by files.transfer.collectAsState()
    val notice by files.message.collectAsState()
    var deleteTarget by remember { mutableStateOf<RemoteFile?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) files.upload(uri)
    }

    LaunchedEffect(Unit) { files.ensureLoaded() }
    BackHandler(enabled = path != FilesController.HOME) {
        if (path == "/") files.open(FilesController.HOME) else files.up()
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            shortcuts.forEach { (label, target) ->
                ShortcutChip(label, selected = path == target) { files.open(target) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedKey(
                onClick = files::up,
                modifier = Modifier.size(44.dp),
                enabled = path != "/",
                halo = false
            ) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Üst klasör", tint = OnSurface, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Breadcrumb(path, onNavigate = files::open, modifier = Modifier.weight(1f))
            IconButton(onClick = files::refresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Yenile", tint = Cyan)
            }
        }
        Spacer(Modifier.height(10.dp))
        GlowButton(
            text = "Telefondan dosya gönder",
            onClick = { picker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.FileUpload,
            loading = transfer != null
        )
        transfer?.let {
            Spacer(Modifier.height(10.dp))
            TransferCard(it)
        }
        NoticeBanner(notice, onDismiss = files::clearMessage, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                isLoading && entries.isEmpty() -> CircularProgressIndicator(
                    color = Cyan,
                    strokeWidth = 2.dp,
                    modifier = Modifier.align(Alignment.Center)
                )
                entries.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = OnSurfaceDim, modifier = Modifier.size(44.dp))
                    Text("Bu klasör boş ya da okunamıyor", color = OnSurfaceDim, fontSize = 14.sp)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(entries, key = { it.path }) { file ->
                        FileRow(
                            file = file,
                            onOpen = { files.open(file.path) },
                            onDownload = { files.download(file) },
                            onInstall = { files.install(file) },
                            onDelete = { deleteTarget = file },
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { file ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = CardColor,
            title = { Text(if (file.isDirectory) "Klasörü sil" else "Dosyayı sil", color = OnSurface) },
            text = {
                Text(
                    if (file.isDirectory) "\"${file.name}\" klasörü ve içindeki her şey TV'den silinsin mi?"
                    else "\"${file.name}\" TV'den silinsin mi?",
                    color = OnSurfaceDim
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    files.delete(file)
                    deleteTarget = null
                }) { Text("Sil", color = ErrorColor) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("İptal", color = Cyan) }
            }
        )
    }
}

@Composable
private fun ShortcutChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Cyan else OnSurfaceDim,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Cyan.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f))
            .border(1.dp, if (selected) Cyan.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.06f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun Breadcrumb(path: String, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val segments = path.split('/').filter { it.isNotEmpty() }
    val scroll = rememberScrollState()
    // Keep the deepest folder visible as the path grows.
    LaunchedEffect(path, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    Row(modifier = modifier.horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
        Crumb("/", isLast = segments.isEmpty()) { onNavigate("/") }
        segments.forEachIndexed { index, segment ->
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = OnSurfaceDim, modifier = Modifier.size(16.dp))
            val target = "/" + segments.take(index + 1).joinToString("/")
            Crumb(segment, isLast = index == segments.lastIndex) { onNavigate(target) }
        }
    }
}

@Composable
private fun Crumb(label: String, isLast: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (isLast) OnSurface else OnSurfaceDim,
        fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 14.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !isLast, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    )
}

@Composable
private fun FileRow(
    file: RemoteFile,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    val (icon, tint) = iconFor(file)
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GlassBrush)
            .border(1.dp, GlassBorderBrush, shape)
            .clickable { if (file.isDirectory) onOpen() else menuOpen = true }
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(tint.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, color = OnSurface, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = if (file.isDirectory) formatDate(file.modifiedSeconds)
                else "${formatSize(file.size)} · ${formatDate(file.modifiedSeconds)}",
                color = OnSurfaceDim,
                fontSize = 11.sp
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "İşlemler", tint = OnSurfaceDim)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (file.isDirectory) {
                    MenuItem("Aç", Icons.Default.FolderOpen, Cyan) { menuOpen = false; onOpen() }
                } else {
                    MenuItem("Telefona indir", Icons.Default.FileDownload, Cyan) { menuOpen = false; onDownload() }
                    if (file.extension == "apk") {
                        MenuItem("TV'ye kur", Icons.Default.InstallMobile, SuccessGreen) { menuOpen = false; onInstall() }
                    }
                }
                MenuItem("Sil", Icons.Default.Delete, ErrorColor) { menuOpen = false; onDelete() }
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = OnSurface) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint) },
        onClick = onClick
    )
}

private fun iconFor(file: RemoteFile): Pair<ImageVector, Color> = when {
    file.isDirectory -> Icons.Default.Folder to Amber
    file.extension in IMAGE_EXT -> Icons.Default.Image to Color(0xFF64B5F6)
    file.extension in VIDEO_EXT -> Icons.Default.Movie to Purple
    file.extension in AUDIO_EXT -> Icons.Default.MusicNote to SuccessGreen
    file.extension == "apk" -> Icons.Default.Android to SuccessGreen
    file.extension in ARCHIVE_EXT -> Icons.Default.FolderZip to Amber
    else -> Icons.Default.Description to OnSurfaceDim
}
