package com.smdozk.tvremote.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smdozk.tvremote.model.ConnectionState
import com.smdozk.tvremote.model.DiscoveredTv
import com.smdozk.tvremote.model.SavedTv
import com.smdozk.tvremote.model.TvKind
import com.smdozk.tvremote.viewmodel.ScanState
import com.smdozk.tvremote.ui.components.GlassCard
import com.smdozk.tvremote.ui.components.GlowButton
import com.smdozk.tvremote.ui.components.ThemeToggle
import com.smdozk.tvremote.ui.theme.Cyan
import com.smdozk.tvremote.ui.theme.ErrorColor
import com.smdozk.tvremote.ui.theme.GradientCyanPurple
import com.smdozk.tvremote.ui.theme.OnAccent
import com.smdozk.tvremote.ui.theme.OnSurface
import com.smdozk.tvremote.ui.theme.OnSurfaceDim
import com.smdozk.tvremote.ui.theme.PowerRed
import com.smdozk.tvremote.ui.theme.Purple
import com.smdozk.tvremote.ui.theme.SurfaceVariant
import com.smdozk.tvremote.ui.theme.Palette
import com.smdozk.tvremote.viewmodel.MainViewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val helpSteps = listOf(
    "Telefon ve TV aynı Wi-Fi ağında olsun; uygulama TV'yi kendisi bulur ve bağlanır",
    "webOS (yeni Sunny modelleri): ilk bağlantıda TV'de çıkan isteği kumandayla onayla, sonra hep otomatik bağlanır",
    "Telefondan açmak için TV ayarlarında \"Wi-Fi ile aç\" / \"Mobil cihazla TV'yi aç\" seçeneğini aç",
    "Bulunamazsa TV ağ/cihaz ayarlarında \"LG Connect Apps\" varsa aç ya da IP adresini elle yaz",
    "Android TV: Ayarlar → Cihaz Tercihleri → Hakkında → \"Yapı\"ya 7 kez bas, sonra Geliştirici seçenekleri → USB hata ayıklama"
)

@Composable
fun ConnectScreen(vm: MainViewModel) {
    val state by vm.connectionState.collectAsState()
    val error by vm.connectionError.collectAsState()

    var ip by rememberSaveable { mutableStateOf(vm.savedHost) }
    var port by rememberSaveable { mutableStateOf(vm.savedPort.toString()) }

    val scanState by vm.scanState.collectAsState()
    val pairingPrompt by vm.pairingPrompt.collectAsState()
    val discovered by vm.discovered.collectAsState()
    val savedTv by vm.savedTv.collectAsState()
    val status by vm.status.collectAsState()
    val pendingLink by vm.pendingLink.collectAsState()

    val focusManager = LocalFocusManager.current
    val isConnecting = state == ConnectionState.CONNECTING
    val connect = {
        focusManager.clearFocus()
        if (ip.isNotBlank() && !isConnecting) vm.connect(ip, port.toIntOrNull() ?: 5555)
    }

    LaunchedEffect(Unit) { vm.onConnectScreenShown() }

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackground(Modifier.fillMaxSize())
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                HeroBadge(connecting = isConnecting)
                Spacer(Modifier.height(20.dp))
                Text(
                    "TV Remote",
                    style = TextStyle(brush = GradientCyanPurple, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                )
                Text("Sunny TV · webOS ve Android TV", fontSize = 13.sp, color = OnSurfaceDim)

                AnimatedVisibility(visible = status != null, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        status.orEmpty(),
                        color = Cyan,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }

                pendingLink?.let { link ->
                    PendingLinkCard(link, onCancel = vm::clearPendingLink, modifier = Modifier.padding(top = 18.dp))
                }

                Spacer(Modifier.height(28.dp))

                DiscoveryCard(
                    state = scanState,
                    found = discovered,
                    saved = savedTv,
                    enabled = !isConnecting,
                    onScan = vm::scanNetwork,
                    onPick = { tv ->
                        ip = tv.host
                        focusManager.clearFocus()
                        vm.connect(tv)
                    },
                    onPowerOn = vm::powerOn
                )

                Spacer(Modifier.height(14.dp))

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Bağlantı", fontWeight = FontWeight.SemiBold, color = OnSurface, fontSize = 15.sp)
                        OutlinedTextField(
                            value = ip,
                            onValueChange = { ip = it },
                            label = { Text("TV IP adresi") },
                            placeholder = { Text("192.168.1.100") },
                            leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null, tint = Cyan) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            singleLine = true,
                            enabled = !isConnecting,
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { value -> port = value.filter(Char::isDigit).take(5) },
                            label = { Text("ADB portu (yalnızca Android TV)") },
                            leadingIcon = { Icon(Icons.Default.SettingsEthernet, contentDescription = null, tint = Cyan) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { connect() }),
                            singleLine = true,
                            enabled = !isConnecting,
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                AnimatedVisibility(visible = error != null && !isConnecting, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        text = error.orEmpty(),
                        color = ErrorColor,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .padding(top = 14.dp)
                            .fillMaxWidth()
                            .background(ErrorColor.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }

                Spacer(Modifier.height(18.dp))

                GlowButton(
                    text = if (isConnecting) "Bağlanıyor…" else "Bağlan",
                    onClick = connect,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Link,
                    enabled = ip.isNotBlank(),
                    loading = isConnecting
                )

                AnimatedVisibility(visible = isConnecting, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        if (pairingPrompt) "TV ekranında bağlantı isteği çıktı: kumandayla onayla."
                        else "TV ekranında izin sorulursa kumandayla onayla.",
                        color = if (pairingPrompt) Cyan else OnSurfaceDim,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                Spacer(Modifier.height(28.dp))
                HelpCard()
            }
        }
        ThemeToggle(
            vm::setLightTheme,
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp)
        )
    }
}

@Composable
private fun PendingLinkCard(link: String, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Link, contentDescription = null, tint = Cyan, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Bağlanınca TV'de açılacak", color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(link, color = OnSurfaceDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onCancel) { Text("Vazgeç", color = OnSurfaceDim) }
        }
    }
}

@Composable
private fun DiscoveryCard(
    state: ScanState,
    found: List<DiscoveredTv>,
    saved: SavedTv?,
    enabled: Boolean,
    onScan: () -> Unit,
    onPick: (DiscoveredTv) -> Unit,
    onPowerOn: () -> Unit
) {
    val savedFound = saved != null && found.any { isSameTv(saved, it) }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WifiFind, contentDescription = null, tint = Cyan, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ağdaki TV'ler", fontWeight = FontWeight.SemiBold, color = OnSurface, fontSize = 15.sp)
                    Text(
                        when {
                            state == ScanState.Scanning && found.isEmpty() -> "Wi-Fi ağında TV aranıyor…"
                            state == ScanState.Scanning -> "Aranıyor · bağlanmak için TV'ye dokun"
                            state == ScanState.NoWifi -> "Telefon Wi-Fi'ye bağlı değil"
                            state == ScanState.Done && found.isEmpty() -> "Ağda TV bulunamadı"
                            state == ScanState.Done -> "Bağlanmak için TV'ye dokun"
                            else -> "TV'ni IP yazmadan bul"
                        },
                        color = OnSurfaceDim,
                        fontSize = 12.sp
                    )
                }
                if (state == ScanState.Scanning) {
                    CircularProgressIndicator(color = Cyan, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                } else {
                    TextButton(onClick = onScan, enabled = enabled) {
                        Text(if (state == ScanState.Idle) "Tara" else "Yeniden tara", color = Cyan)
                    }
                }
            }
            found.forEach { tv ->
                DiscoveredRow(tv, isLast = saved != null && isSameTv(saved, tv), enabled = enabled) { onPick(tv) }
            }
            // The saved TV is not answering: it is probably off, so offer Wake-on-LAN.
            if (saved != null && saved.macs.isNotEmpty() && !savedFound && state != ScanState.Scanning) {
                PowerOnRow(saved, enabled, onPowerOn)
            }
        }
    }
}

private fun isSameTv(saved: SavedTv, tv: DiscoveredTv): Boolean =
    if (saved.uuid != null && tv.uuid != null) saved.uuid == tv.uuid else saved.host == tv.host

@Composable
private fun PowerOnRow(saved: SavedTv, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(PowerRed.copy(alpha = 0.10f))
            .border(1.dp, PowerRed.copy(alpha = 0.35f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = PowerRed, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("TV'yi aç", color = OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                "${saved.name ?: "Kayıtlı TV"} · ${saved.host}",
                color = OnSurfaceDim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = PowerRed)
    }
}

@Composable
private fun DiscoveredRow(tv: DiscoveredTv, isLast: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Cyan.copy(alpha = 0.08f))
            .border(1.dp, Cyan.copy(alpha = 0.25f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Tv, contentDescription = null, tint = Cyan, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tv.name ?: if (tv.kind == TvKind.WebOs) "webOS TV" else "Android TV",
                color = OnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(
                    tv.host,
                    tv.model,
                    if (tv.kind == TvKind.WebOs) "webOS" else "Android (ADB)",
                    "son bağlanılan".takeIf { isLast }
                ).joinToString(" · "),
                color = if (isLast) Cyan else OnSurfaceDim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Cyan)
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Cyan,
    unfocusedBorderColor = SurfaceVariant,
    focusedLabelColor = Cyan,
    cursorColor = Cyan
)

@Composable
private fun AmbientBackground(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "ambient")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "phase"
    )
    Spacer(
        modifier
            // Phase is read only while drawing this layer: the screen itself never recomposes per frame.
            .graphicsLayer()
            .drawBehind {
                val t = phase.value * 2f * PI.toFloat()
                val w = size.width
                val h = size.height
                val cyanCenter = Offset(w * (0.28f + 0.10f * cos(t)), h * (0.20f + 0.05f * sin(t)))
                val purpleCenter = Offset(w * (0.78f + 0.08f * sin(t)), h * (0.72f + 0.06f * cos(t)))
                drawCircle(
                    Brush.radialGradient(listOf(Palette.ambientA, Color.Transparent), cyanCenter, w * 0.8f),
                    radius = w * 0.8f,
                    center = cyanCenter
                )
                drawCircle(
                    Brush.radialGradient(listOf(Palette.ambientB, Color.Transparent), purpleCenter, w * 0.85f),
                    radius = w * 0.85f,
                    center = purpleCenter
                )
            }
    )
}

@Composable
private fun HeroBadge(connecting: Boolean) {
    Box(
        modifier = Modifier
            .size(120.dp)
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(Cyan.copy(alpha = 0.30f), Color.Transparent)),
                    radius = size.minDimension / 2f
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(visible = connecting, enter = fadeIn(), exit = fadeOut()) {
            SpinningRing(Modifier.size(108.dp))
        }
        Box(
            modifier = Modifier
                .size(78.dp)
                .background(GradientCyanPurple, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Tv, contentDescription = null, tint = OnAccent, modifier = Modifier.size(38.dp))
        }
    }
}

@Composable
private fun SpinningRing(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "spin")
    val angle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "angle"
    )
    Spacer(
        modifier
            .graphicsLayer()
            .drawBehind {
                val stroke = 3.dp.toPx()
                rotate(angle.value) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(Color.Transparent, Cyan, Purple, Color.Transparent)),
                        startAngle = 0f,
                        sweepAngle = 300f,
                        useCenter = false,
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }
    )
}

@Composable
private fun HelpCard() {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Nasıl bağlanılır?", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Cyan)
            helpSteps.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(Cyan.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${index + 1}", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(step, color = OnSurfaceDim, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}
