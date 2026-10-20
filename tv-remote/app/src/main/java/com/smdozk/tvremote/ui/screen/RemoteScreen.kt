package com.smdozk.tvremote.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smdozk.tvremote.adb.TvKeyCode
import com.smdozk.tvremote.ui.components.AnimatedIconKey
import com.smdozk.tvremote.ui.components.AnimatedKey
import com.smdozk.tvremote.ui.components.AnimatedTextKey
import com.smdozk.tvremote.ui.theme.*
import com.smdozk.tvremote.viewmodel.MainViewModel

@Composable
fun RemoteScreen(vm: MainViewModel) {
    var inputText by remember { mutableStateOf("") }
    var showTextInput by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top row — power, input, settings, back
        GlassCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedIconKey(
                    icon = Icons.Default.PowerSettingsNew,
                    label = "Güç",
                    onClick = { vm.sendKey(TvKeyCode.POWER) },
                    tint = Color(0xFFFF5252),
                    glowColor = Color(0x55FF5252)
                )
                AnimatedIconKey(
                    icon = Icons.Default.Input,
                    label = "Kaynak",
                    onClick = { vm.sendKey(TvKeyCode.TV_INPUT) },
                    tint = Cyan,
                    glowColor = CyanGlow
                )
                AnimatedIconKey(
                    icon = Icons.Default.Settings,
                    label = "Ayarlar",
                    onClick = { vm.sendKey(TvKeyCode.SETTINGS) }
                )
                AnimatedIconKey(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    label = "Geri",
                    onClick = { vm.sendKey(TvKeyCode.BACK) }
                )
            }
        }

        // Home button
        AnimatedKey(
            onClick = { vm.sendKey(TvKeyCode.HOME) },
            size = 56.dp,
            backgroundBrush = GradientCyanPurple,
            glowColor = CyanGlow
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Home, contentDescription = "Ana Ekran", tint = Color.Black, modifier = Modifier.size(22.dp))
            }
        }

        // D-Pad
        GlassCard {
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .size(220.dp),
                contentAlignment = Alignment.Center
            ) {
                DPadRing()

                // OK center
                AnimatedKey(
                    onClick = { vm.sendKey(TvKeyCode.DPAD_CENTER) },
                    size = 72.dp,
                    backgroundBrush = GradientCyan,
                    glowColor = CyanGlow
                ) {
                    Text("OK", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 18.sp)
                }

                // Directional arrows
                IconButton(
                    onClick = { vm.sendKey(TvKeyCode.DPAD_UP) },
                    modifier = Modifier.size(56.dp).align(Alignment.TopCenter)
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Yukarı", tint = OnSurface, modifier = Modifier.size(32.dp))
                }
                IconButton(
                    onClick = { vm.sendKey(TvKeyCode.DPAD_DOWN) },
                    modifier = Modifier.size(56.dp).align(Alignment.BottomCenter)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Aşağı", tint = OnSurface, modifier = Modifier.size(32.dp))
                }
                IconButton(
                    onClick = { vm.sendKey(TvKeyCode.DPAD_LEFT) },
                    modifier = Modifier.size(56.dp).align(Alignment.CenterStart)
                ) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Sol", tint = OnSurface, modifier = Modifier.size(32.dp))
                }
                IconButton(
                    onClick = { vm.sendKey(TvKeyCode.DPAD_RIGHT) },
                    modifier = Modifier.size(56.dp).align(Alignment.CenterEnd)
                ) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Sağ", tint = OnSurface, modifier = Modifier.size(32.dp))
                }
            }
        }

        // Volume + mute
        GlassCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SES", fontSize = 10.sp, color = OnSurfaceDim, fontWeight = FontWeight.SemiBold)
                    AnimatedKey(
                        onClick = { vm.sendKey(TvKeyCode.VOLUME_UP) },
                        size = 52.dp,
                        backgroundBrush = Brush.radialGradient(listOf(Color(0xFF0D2025), CardColor)),
                        glowColor = CyanGlow
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "+", tint = Cyan, modifier = Modifier.size(22.dp))
                    }
                    AnimatedKey(
                        onClick = { vm.sendKey(TvKeyCode.VOLUME_DOWN) },
                        size = 52.dp,
                        backgroundBrush = Brush.radialGradient(listOf(Color(0xFF0D2025), CardColor)),
                        glowColor = CyanGlow
                    ) {
                        Icon(Icons.Default.VolumeDown, contentDescription = "-", tint = CyanDim, modifier = Modifier.size(22.dp))
                    }
                }
                AnimatedIconKey(
                    icon = Icons.Default.VolumeOff,
                    label = "Sustur",
                    onClick = { vm.sendKey(TvKeyCode.VOLUME_MUTE) },
                    tint = Color(0xFFFFAB40),
                    glowColor = Color(0x55FFAB40)
                )
            }
        }

        // Media controls
        GlassCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedIconKey(Icons.Default.SkipPrevious, "Önceki", { vm.sendKey(TvKeyCode.MEDIA_PREVIOUS) }, size = 44.dp, iconSize = 18.dp)
                AnimatedIconKey(Icons.Default.Replay10, "Geri", { vm.sendKey(TvKeyCode.MEDIA_REWIND) }, size = 44.dp, iconSize = 18.dp)
                AnimatedKey(
                    onClick = { vm.sendKey(TvKeyCode.MEDIA_PLAY_PAUSE) },
                    size = 62.dp,
                    backgroundBrush = GradientCyan,
                    glowColor = CyanGlow
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Oynat", tint = Color.Black, modifier = Modifier.size(26.dp))
                }
                AnimatedIconKey(Icons.Default.Forward10, "İleri", { vm.sendKey(TvKeyCode.MEDIA_FAST_FORWARD) }, size = 44.dp, iconSize = 18.dp)
                AnimatedIconKey(Icons.Default.SkipNext, "Sonraki", { vm.sendKey(TvKeyCode.MEDIA_NEXT) }, size = 44.dp, iconSize = 18.dp)
            }
        }

        // Number pad
        GlassCard {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val numRows = listOf(
                    listOf(1 to TvKeyCode.NUM_1, 2 to TvKeyCode.NUM_2, 3 to TvKeyCode.NUM_3),
                    listOf(4 to TvKeyCode.NUM_4, 5 to TvKeyCode.NUM_5, 6 to TvKeyCode.NUM_6),
                    listOf(7 to TvKeyCode.NUM_7, 8 to TvKeyCode.NUM_8, 9 to TvKeyCode.NUM_9),
                )
                numRows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (label, keyCode) ->
                            AnimatedTextKey(label.toString(), { vm.sendKey(keyCode) })
                        }
                    }
                }
                AnimatedTextKey("0", { vm.sendKey(TvKeyCode.NUM_0) })
            }
        }

        // Text input
        if (showTextInput) {
            GlassCard {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Metin gir...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Cyan,
                            unfocusedBorderColor = SurfaceVariant
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    AnimatedKey(
                        onClick = { vm.inputText(inputText); inputText = "" },
                        size = 48.dp,
                        backgroundBrush = GradientCyan,
                        glowColor = CyanGlow
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Gönder", tint = Color.Black, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { showTextInput = !showTextInput },
            modifier = Modifier.fillMaxWidth(),
            border = androidx.compose.foundation.BorderStroke(1.dp, Cyan.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Keyboard, contentDescription = null, tint = Cyan, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (showTextInput) "Klavyeyi Gizle" else "Metin Gir", color = Cyan)
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DPadRing() {
    val infiniteTransition = rememberInfiniteTransition(label = "ring")
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringAlpha"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2 - 8f
        drawCircle(
            color = Cyan.copy(alpha = ringAlpha),
            radius = radius,
            center = center,
            style = Stroke(width = 1.5f, cap = StrokeCap.Round)
        )
        drawCircle(
            color = Cyan.copy(alpha = ringAlpha * 0.5f),
            radius = radius - 14f,
            center = center,
            style = Stroke(width = 0.8f)
        )
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1A1A38), Color(0xFF111125))
                )
            )
            .border(0.8.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp)),
        content = content
    )
}
