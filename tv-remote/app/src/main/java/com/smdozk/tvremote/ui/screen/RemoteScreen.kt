package com.smdozk.tvremote.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smdozk.tvremote.adb.TvKeyCode
import com.smdozk.tvremote.ui.theme.CardColor
import com.smdozk.tvremote.ui.theme.Cyan
import com.smdozk.tvremote.ui.theme.SurfaceVariant
import com.smdozk.tvremote.viewmodel.MainViewModel

@Composable
fun RemoteScreen(vm: MainViewModel) {
    var inputText by remember { mutableStateOf("") }
    var showTextInput by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row 1: Power + Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RemoteIconButton(Icons.Default.PowerSettingsNew, "Güç", tint = Color(0xFFEF5350)) {
                vm.sendKey(TvKeyCode.POWER)
            }
            RemoteIconButton(Icons.Default.Input, "Kaynak") { vm.sendKey(TvKeyCode.TV_INPUT) }
            RemoteIconButton(Icons.Default.Settings, "Ayarlar") { vm.sendKey(TvKeyCode.SETTINGS) }
            RemoteIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Geri") { vm.sendKey(TvKeyCode.BACK) }
        }

        HorizontalDivider(color = SurfaceVariant, thickness = 1.dp)

        // Row 2: Home
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RemoteIconButton(Icons.Default.Home, "Ana Ekran") { vm.sendKey(TvKeyCode.HOME) }
        }

        // D-Pad
        DPad(vm)

        HorizontalDivider(color = SurfaceVariant, thickness = 1.dp)

        // Volume + Mute row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SES", fontSize = 10.sp, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                RemoteIconButton(Icons.Default.VolumeUp, "+") { vm.sendKey(TvKeyCode.VOLUME_UP) }
                Spacer(Modifier.height(4.dp))
                RemoteIconButton(Icons.Default.VolumeDown, "-") { vm.sendKey(TvKeyCode.VOLUME_DOWN) }
            }
            RemoteIconButton(Icons.Default.VolumeOff, "Sustur") { vm.sendKey(TvKeyCode.VOLUME_MUTE) }
        }

        HorizontalDivider(color = SurfaceVariant, thickness = 1.dp)

        // Media controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RemoteIconButton(Icons.Default.SkipPrevious, "Önceki") { vm.sendKey(TvKeyCode.MEDIA_PREVIOUS) }
            RemoteIconButton(Icons.Default.Replay10, "Geri sar") { vm.sendKey(TvKeyCode.MEDIA_REWIND) }
            RemoteIconButton(Icons.Default.PlayArrow, "Oynat/Dur", size = 52.dp, tint = Cyan) {
                vm.sendKey(TvKeyCode.MEDIA_PLAY_PAUSE)
            }
            RemoteIconButton(Icons.Default.Forward10, "İleri sar") { vm.sendKey(TvKeyCode.MEDIA_FAST_FORWARD) }
            RemoteIconButton(Icons.Default.SkipNext, "Sonraki") { vm.sendKey(TvKeyCode.MEDIA_NEXT) }
        }

        HorizontalDivider(color = SurfaceVariant, thickness = 1.dp)

        // Number pad
        NumberPad(vm)

        HorizontalDivider(color = SurfaceVariant, thickness = 1.dp)

        // Text input
        if (showTextInput) {
            Card(colors = CardDefaults.cardColors(containerColor = CardColor), modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Metin gir...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            vm.inputText(inputText)
                            inputText = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan)
                    ) {
                        Text("Gönder", color = Color.Black)
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { showTextInput = !showTextInput },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Keyboard, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (showTextInput) "Klavyeyi Gizle" else "Metin Gir")
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DPad(vm: MainViewModel) {
    Box(
        modifier = Modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        // OK button (center)
        Button(
            onClick = { vm.sendKey(TvKeyCode.DPAD_CENTER) },
            modifier = Modifier.size(64.dp).align(Alignment.Center),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Cyan),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("OK", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 16.sp)
        }

        // Up
        IconButton(
            onClick = { vm.sendKey(TvKeyCode.DPAD_UP) },
            modifier = Modifier.size(52.dp).align(Alignment.TopCenter)
        ) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Yukarı", tint = Color.White, modifier = Modifier.size(36.dp))
        }

        // Down
        IconButton(
            onClick = { vm.sendKey(TvKeyCode.DPAD_DOWN) },
            modifier = Modifier.size(52.dp).align(Alignment.BottomCenter)
        ) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Aşağı", tint = Color.White, modifier = Modifier.size(36.dp))
        }

        // Left
        IconButton(
            onClick = { vm.sendKey(TvKeyCode.DPAD_LEFT) },
            modifier = Modifier.size(52.dp).align(Alignment.CenterStart)
        ) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Sol", tint = Color.White, modifier = Modifier.size(36.dp))
        }

        // Right
        IconButton(
            onClick = { vm.sendKey(TvKeyCode.DPAD_RIGHT) },
            modifier = Modifier.size(52.dp).align(Alignment.CenterEnd)
        ) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Sağ", tint = Color.White, modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun NumberPad(vm: MainViewModel) {
    val numKeys = listOf(
        listOf(1 to TvKeyCode.NUM_1, 2 to TvKeyCode.NUM_2, 3 to TvKeyCode.NUM_3),
        listOf(4 to TvKeyCode.NUM_4, 5 to TvKeyCode.NUM_5, 6 to TvKeyCode.NUM_6),
        listOf(7 to TvKeyCode.NUM_7, 8 to TvKeyCode.NUM_8, 9 to TvKeyCode.NUM_9),
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        numKeys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, keyCode) ->
                    NumButton(label.toString()) { vm.sendKey(keyCode) }
                }
            }
        }
        NumButton("0") { vm.sendKey(TvKeyCode.NUM_0) }
    }
}

@Composable
private fun NumButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(64.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RemoteIconButton(
    icon: ImageVector,
    label: String,
    size: Dp = 48.dp,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(size)) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(size * 0.55f))
        }
        Text(label, fontSize = 9.sp, color = Color.Gray)
    }
}
