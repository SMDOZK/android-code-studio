package com.smdozk.tvremote.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
