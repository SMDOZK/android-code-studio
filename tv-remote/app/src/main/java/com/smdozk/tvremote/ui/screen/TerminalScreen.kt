package com.smdozk.tvremote.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smdozk.tvremote.model.TerminalEntry
import com.smdozk.tvremote.ui.theme.CardColor
import com.smdozk.tvremote.ui.theme.Cyan
import com.smdozk.tvremote.ui.theme.ErrorColor
import com.smdozk.tvremote.ui.theme.SurfaceVariant
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

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar
        Surface(color = CardColor) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ADB Shell", fontSize = 13.sp, color = Cyan, fontWeight = FontWeight.SemiBold)
                Row {
                    if (isExecuting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Cyan, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    IconButton(onClick = vm::clearTerminal, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Temizle", tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Quick commands row
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickCommands.forEach { (label, cmd) ->
                AssistChip(
                    onClick = { vm.executeCommand(cmd) },
                    label = { Text(label, fontSize = 11.sp) },
                    enabled = !isExecuting,
                    modifier = Modifier.height(28.dp)
                )
            }
        }

        HorizontalDivider(color = SurfaceVariant)

        // Terminal output
        if (history.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Komut girin veya yukarıdaki hızlı komutları kullanın",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history) { entry ->
                    TerminalEntryCard(entry)
                }
            }
        }

        HorizontalDivider(color = SurfaceVariant)

        // Input row
        Row(
            modifier = Modifier
                .background(CardColor)
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$ ", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                placeholder = { Text("komut...", fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                enabled = !isExecuting
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
                    tint = if (command.isNotBlank()) Cyan else Color.Gray
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
            .background(SurfaceVariant, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$ ${entry.command}",
                color = Cyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (entry.output.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = entry.output,
                color = if (entry.isError) ErrorColor else Color(0xFFB0B0B0),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}
