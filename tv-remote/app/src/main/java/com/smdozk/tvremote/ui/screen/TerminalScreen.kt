package com.smdozk.tvremote.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.smdozk.tvremote.model.LogLine
import com.smdozk.tvremote.viewmodel.LogcatController
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smdozk.tvremote.model.TerminalEntry
import com.smdozk.tvremote.ui.theme.*
import com.smdozk.tvremote.viewmodel.MainViewModel

private val quickCommands = listOf(
    "ps" to "ps -A",
    "ls" to "ls /sdcard",
    "net" to "netstat -tlnp",
    "mem" to "cat /proc/meminfo",
    "cpu" to "cat /proc/cpuinfo | grep 'model name'",
    "ip" to "ip addr show",
    "log" to "logcat -d -t 30",
    "pkg" to "pm list packages -3",
    "date" to "date",
    "uptime" to "uptime",
    "df" to "df -h",
    "env" to "env",
)

@Composable
fun TerminalScreen(vm: MainViewModel) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        ModeSwitch(
            labels = listOf("Shell", "Canlı log"),
            selected = mode,
            onSelect = { mode = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
        Box(modifier = Modifier.weight(1f)) {
            if (mode == 0) ShellPanel(vm) else LogPanel(vm.logcat)
        }
    }
}

@Composable
private fun ModeSwitch(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val position by animateFloatAsState(
        targetValue = selected.toFloat(),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 700f),
        label = "mode"
    )
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, GlassBorderBrush, CircleShape)
            .padding(4.dp)
    ) {
        val segment = maxWidth / labels.size
        Box(
            modifier = Modifier
                .width(segment)
                .fillMaxHeight()
                .graphicsLayer { translationX = position * segment.toPx() }
                .background(GradientCyan, CircleShape)
        )
        Row(modifier = Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (index == selected) OnAccent else OnSurfaceDim,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LogPanel(logcat: LogcatController) {
    val lines by logcat.lines.collectAsState()
    val running by logcat.isRunning.collectAsState()
    var filter by rememberSaveable { mutableStateOf("") }
    var frozen by remember { mutableStateOf<List<LogLine>?>(null) }
    val listState = rememberLazyListState()

    DisposableEffect(logcat) {
        logcat.start()
        onDispose { logcat.stop() }
    }

    val source = frozen ?: lines
    val visible = remember(source, filter) {
        if (filter.isBlank()) source
        else source.filter { it.tag.contains(filter, ignoreCase = true) || it.message.contains(filter, ignoreCase = true) }
    }
    // Follow the tail unless the user has scrolled up to read.
    val following by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(visible.size) {
        if (following && visible.isNotEmpty()) listState.scrollToItem(visible.lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text("Filtre (etiket veya mesaj)", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, tint = OnSurfaceDim) },
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, color = OnSurface),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Cyan, unfocusedBorderColor = SurfaceVariant),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { frozen = if (frozen == null) lines else null }) {
                Icon(
                    if (frozen == null) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (frozen == null) "Duraklat" else "Devam et",
                    tint = Cyan
                )
            }
            IconButton(onClick = {
                frozen = null
                logcat.clear()
            }) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Temizle", tint = OnSurfaceDim)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(if (running && frozen == null) Color(0xFF4CAF50) else OnSurfaceDim)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    frozen != null -> "Duraklatıldı"
                    running -> "Canlı"
                    else -> "Bağlantı yok"
                } + " · ${visible.size} satır",
                color = OnSurfaceDim,
                fontSize = 11.sp
            )
        }
        HorizontalDivider(color = SurfaceVariant, thickness = 0.6.dp)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            items(visible, key = { it.id }) { line -> LogRow(line) }
        }
    }
}

@Composable
private fun LogRow(line: LogLine) {
    val color = when (line.level) {
        'E', 'F', 'A' -> ErrorColor
        'W' -> Amber
        'I' -> Cyan
        'D' -> OnSurface
        else -> OnSurfaceDim
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            line.level.toString(),
            color = color,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.width(14.dp)
        )
        Text(
            text = buildAnnotatedString {
                if (line.tag.isNotEmpty()) {
                    withStyle(SpanStyle(color = color)) { append(line.tag) }
                    append(": ")
                }
                append(line.message)
            },
            color = Color(0xFFB4B4D8),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun ShellPanel(vm: MainViewModel) {
    val history by vm.terminalHistory.collectAsState()
    val isExecuting by vm.isExecuting.collectAsState()
    val listState = rememberLazyListState()
    var command by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) listState.animateScrollToItem(history.size - 1)
    }

    fun submit() {
        if (command.isNotBlank() && !isExecuting) {
            vm.executeCommand(command.trim())
            command = ""
            focusManager.clearFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Toolbar
        Row(
            modifier = Modifier
                .background(Surface)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (isExecuting) Cyan else Color(0xFF4CAF50))
                )
                Text("ADB Shell", fontSize = 14.sp, color = Cyan, fontWeight = FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isExecuting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Cyan, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(onClick = vm::clearTerminal, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Temizle", tint = OnSurfaceDim, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Quick command chips
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickCommands.forEach { (label, cmd) ->
                AssistChip(
                    onClick = { vm.executeCommand(cmd) },
                    label = { Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                    enabled = !isExecuting,
                    modifier = Modifier.height(28.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = SurfaceVariant,
                        labelColor = Cyan
                    ),
                    border = AssistChipDefaults.assistChipBorder(
                        enabled = !isExecuting,
                        borderColor = CyanGlow
                    )
                )
            }
        }

        HorizontalDivider(color = SurfaceVariant, thickness = 0.6.dp)

        // Output
        if (history.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Komut girin veya hızlı komutları kullanın", color = OnSurfaceDim, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(history) { entry -> TerminalEntryCard(entry) }
            }
        }

        HorizontalDivider(color = SurfaceVariant, thickness = 0.6.dp)

        // Input
        Row(
            modifier = Modifier
                .background(Surface)
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$ ", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                placeholder = { Text("komut...", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = OnSurfaceDim) },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = OnSurface),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                enabled = !isExecuting,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan,
                    unfocusedBorderColor = SurfaceVariant
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = ::submit,
                enabled = command.isNotBlank() && !isExecuting,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Gönder",
                    tint = if (command.isNotBlank()) Cyan else OnSurfaceDim
                )
            }
        }
    }
}

@Composable
private fun TerminalEntryCard(entry: TerminalEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF161628), Color(0xFF111122))))
            .border(0.6.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Text(
            text = "$ ${entry.command}",
            color = Cyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        if (entry.output.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = entry.output,
                color = if (entry.isError) ErrorColor else Color(0xFF9E9EC8),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}
