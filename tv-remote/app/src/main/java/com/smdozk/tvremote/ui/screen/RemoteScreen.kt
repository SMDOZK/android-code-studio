package com.smdozk.tvremote.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smdozk.tvremote.adb.TvKeyCode
import com.smdozk.tvremote.model.TvInput
import com.smdozk.tvremote.model.TvKind
import com.smdozk.tvremote.ui.components.AnimatedIconKey
import com.smdozk.tvremote.ui.components.AnimatedKey
import com.smdozk.tvremote.ui.components.AnimatedTextKey
import com.smdozk.tvremote.ui.components.DPad
import com.smdozk.tvremote.ui.components.GlassCard
import com.smdozk.tvremote.ui.components.RockerKey
import com.smdozk.tvremote.ui.theme.Amber
import com.smdozk.tvremote.ui.theme.CardColor
import com.smdozk.tvremote.ui.theme.Cyan
import com.smdozk.tvremote.ui.theme.GradientCyan
import com.smdozk.tvremote.ui.theme.GradientCyanPurple
import com.smdozk.tvremote.ui.theme.KeyBrush
import com.smdozk.tvremote.ui.theme.OnAccent
import com.smdozk.tvremote.ui.theme.OnSurface
import com.smdozk.tvremote.ui.theme.OnSurfaceDim
import com.smdozk.tvremote.ui.theme.PowerRed
import com.smdozk.tvremote.ui.theme.SurfaceVariant
import com.smdozk.tvremote.viewmodel.MainViewModel

@Composable
fun RemoteScreen(vm: MainViewModel) {
    val sendKey: (Int) -> Unit = remember(vm) { { code -> vm.sendKey(code) } }
    var showKeyboard by rememberSaveable { mutableStateOf(false) }
    val backend by vm.backend.collectAsState()
    val inputs by vm.inputs.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AnimatedIconKey(Icons.Default.PowerSettingsNew, "Güç", { sendKey(TvKeyCode.POWER) }, tint = PowerRed, glowColor = PowerRed)
            AnimatedIconKey(
                Icons.AutoMirrored.Filled.Input,
                "Kaynak",
                { if (backend == TvKind.WebOs) vm.showInputs() else sendKey(TvKeyCode.TV_INPUT) }
            )
            AnimatedIconKey(Icons.Default.Settings, "Ayarlar", { sendKey(TvKeyCode.SETTINGS) })
            AnimatedIconKey(Icons.AutoMirrored.Filled.VolumeOff, "Sessiz", { sendKey(TvKeyCode.VOLUME_MUTE) }, tint = Amber, glowColor = Amber)
        }

        DPad(onKey = sendKey, modifier = Modifier.fillMaxWidth(0.9f).widthIn(max = 340.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CapsuleKey(Icons.AutoMirrored.Filled.ArrowBack, "Geri", { sendKey(TvKeyCode.BACK) })
            CapsuleKey(Icons.Default.Home, "Ana Ekran", { sendKey(TvKeyCode.HOME) }, accent = true)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RockerKey(
                label = "SES",
                upIcon = Icons.Default.Add, upDescription = "Sesi aç", onUp = { sendKey(TvKeyCode.VOLUME_UP) },
                downIcon = Icons.Default.Remove, downDescription = "Sesi kıs", onDown = { sendKey(TvKeyCode.VOLUME_DOWN) }
            )
            MediaCluster(sendKey)
            RockerKey(
                label = "KANAL",
                upIcon = Icons.Default.KeyboardArrowUp, upDescription = "Sonraki kanal", onUp = { sendKey(TvKeyCode.CHANNEL_UP) },
                downIcon = Icons.Default.KeyboardArrowDown, downDescription = "Önceki kanal", onDown = { sendKey(TvKeyCode.CHANNEL_DOWN) }
            )
        }

        NumberPad(
            sendKey = sendKey,
            keyboardVisible = showKeyboard,
            onToggleKeyboard = { showKeyboard = !showKeyboard }
        )

        AnimatedVisibility(
            visible = showKeyboard,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            TextInputCard(onSend = vm::inputText)
        }

        Spacer(Modifier.height(4.dp))
    }

    inputs?.let { list ->
        InputPicker(inputs = list, onPick = vm::switchInput, onDismiss = vm::dismissInputs)
    }
}

@Composable
private fun InputPicker(inputs: List<TvInput>, onPick: (TvInput) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardColor,
        title = { Text("Kaynak seç", color = OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (inputs.isEmpty()) Text("TV kaynak listesi vermedi.", color = OnSurfaceDim)
                inputs.forEach { input ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(KeyBrush)
                            .clickable { onPick(input) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Input, contentDescription = null, tint = Cyan, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(input.label, color = OnSurface, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Kapat", color = Cyan) }
        }
    )
}

@Composable
private fun MediaCluster(sendKey: (Int) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SmallKey(Icons.Default.SkipPrevious, "Önceki") { sendKey(TvKeyCode.MEDIA_PREVIOUS) }
            SmallKey(Icons.Default.SkipNext, "Sonraki") { sendKey(TvKeyCode.MEDIA_NEXT) }
        }
        AnimatedKey(
            onClick = { sendKey(TvKeyCode.MEDIA_PLAY_PAUSE) },
            modifier = Modifier.size(76.dp),
            background = GradientCyan,
            restGlow = 0.22f
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Oynat / Duraklat", tint = OnAccent, modifier = Modifier.size(36.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SmallKey(Icons.Default.FastRewind, "Geri sar", repeatOnHold = true) { sendKey(TvKeyCode.MEDIA_REWIND) }
            SmallKey(Icons.Default.FastForward, "İleri sar", repeatOnHold = true) { sendKey(TvKeyCode.MEDIA_FAST_FORWARD) }
        }
    }
}

@Composable
private fun SmallKey(icon: ImageVector, label: String, repeatOnHold: Boolean = false, onClick: () -> Unit) {
    AnimatedIconKey(
        icon = icon,
        label = label,
        onClick = onClick,
        size = 46.dp,
        iconSize = 20.dp,
        showLabel = false,
        repeatOnHold = repeatOnHold
    )
}

@Composable
private fun RowScope.CapsuleKey(icon: ImageVector, label: String, onClick: () -> Unit, accent: Boolean = false) {
    val contentColor = if (accent) OnAccent else OnSurface
    AnimatedKey(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(56.dp),
        shape = RoundedCornerShape(percent = 50),
        background = if (accent) GradientCyanPurple else KeyBrush,
        glowColor = if (accent) Color.White else Cyan,
        halo = false
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
            Text(label, color = contentColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun NumberPad(sendKey: (Int) -> Unit, keyboardVisible: Boolean, onToggleKeyboard: () -> Unit) {
    val rows = listOf(
        listOf("1" to TvKeyCode.NUM_1, "2" to TvKeyCode.NUM_2, "3" to TvKeyCode.NUM_3),
        listOf("4" to TvKeyCode.NUM_4, "5" to TvKeyCode.NUM_5, "6" to TvKeyCode.NUM_6),
        listOf("7" to TvKeyCode.NUM_7, "8" to TvKeyCode.NUM_8, "9" to TvKeyCode.NUM_9),
    )
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (label, code) ->
                        AnimatedTextKey(label, { sendKey(code) }, Modifier.weight(1f).height(56.dp))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PadIconKey(
                    icon = Icons.Default.Keyboard,
                    description = if (keyboardVisible) "Klavyeyi gizle" else "Metin gir",
                    tint = if (keyboardVisible) Cyan else OnSurfaceDim,
                    onClick = onToggleKeyboard
                )
                AnimatedTextKey("0", { sendKey(TvKeyCode.NUM_0) }, Modifier.weight(1f).height(56.dp))
                PadIconKey(
                    icon = Icons.AutoMirrored.Filled.Backspace,
                    description = "Sil",
                    tint = OnSurfaceDim,
                    repeatOnHold = true,
                    onClick = { sendKey(TvKeyCode.DEL) }
                )
            }
        }
    }
}

@Composable
private fun RowScope.PadIconKey(
    icon: ImageVector,
    description: String,
    tint: Color,
    repeatOnHold: Boolean = false,
    onClick: () -> Unit
) {
    AnimatedKey(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(56.dp),
        shape = RoundedCornerShape(18.dp),
        halo = false,
        repeatOnHold = repeatOnHold
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun TextInputCard(onSend: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val send = {
        onSend(text)
        text = ""
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("TV'ye yazılacak metin…") },
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan,
                    unfocusedBorderColor = SurfaceVariant,
                    cursorColor = Cyan
                )
            )
            Spacer(Modifier.size(10.dp))
            AnimatedKey(
                onClick = send,
                modifier = Modifier.size(52.dp),
                background = GradientCyan,
                enabled = text.isNotEmpty()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gönder", tint = OnAccent, modifier = Modifier.size(22.dp))
            }
        }
    }
}
