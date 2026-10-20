package com.smdozk.tvremote.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smdozk.tvremote.model.ConnectionState
import com.smdozk.tvremote.ui.theme.*
import com.smdozk.tvremote.viewmodel.MainViewModel

@Composable
fun ConnectScreen(vm: MainViewModel) {
    val state by vm.connectionState.collectAsState()
    val error by vm.connectionError.collectAsState()

    var ip by rememberSaveable { mutableStateOf("192.168.1.") }
    var port by rememberSaveable { mutableStateOf("5555") }

    val focusManager = LocalFocusManager.current
    val isConnecting = state == ConnectionState.CONNECTING

    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Ambient glow background
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.TopCenter)
                .offset(y = 40.dp)
                .scale(pulseScale)
                .background(
                    Brush.radialGradient(
                        listOf(Cyan.copy(alpha = pulseAlpha), Color.Transparent)
                    ),
                    CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // TV Icon with ring
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(if (isConnecting) pulseScale else 1f)
                        .background(
                            Brush.radialGradient(listOf(CyanGlow, Color.Transparent)),
                            CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(GradientCyanPurple)
                ) {
                    Icon(
                        Icons.Default.Tv,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp).align(Alignment.Center)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "TV Remote",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Cyan
            )
            Text(
                "Sunny TV · ADB WiFi",
                fontSize = 13.sp,
                color = OnSurfaceDim
            )

            Spacer(Modifier.height(36.dp))

            // Glass card for inputs
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF1A1A38), Color(0xFF111125))))
                    .border(0.8.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(24.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Bağlantı Ayarları", fontWeight = FontWeight.SemiBold, color = Cyan, fontSize = 15.sp)

                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("TV IP Adresi") },
                    placeholder = { Text("192.168.1.100") },
                    leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null, tint = Cyan) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isConnecting,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cyan,
                        unfocusedBorderColor = SurfaceVariant,
                        focusedLabelColor = Cyan
                    )
                )

                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        if (!isConnecting) vm.connect(ip, port.toIntOrNull() ?: 5555)
                    }),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isConnecting,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cyan,
                        unfocusedBorderColor = SurfaceVariant,
                        focusedLabelColor = Cyan
                    )
                )
            }

            Spacer(Modifier.height(16.dp))

            AnimatedVisibility(visible = isConnecting) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Cyan, modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Bağlanıyor... TV'de ADB yetkilendirme isteğini kabul edin.",
                        fontSize = 12.sp,
                        color = OnSurfaceDim,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            AnimatedVisibility(visible = error != null && !isConnecting) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ErrorColor.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Hata: $error",
                        color = ErrorColor,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Connect button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (!isConnecting && ip.isNotBlank()) GradientCyan else Brush.linearGradient(listOf(SurfaceVariant, SurfaceVariant)))
                    .then(
                        if (!isConnecting && ip.isNotBlank()) Modifier
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = { vm.connect(ip, port.toIntOrNull() ?: 5555) },
                    enabled = !isConnecting && ip.isNotBlank(),
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        disabledContainerColor = SurfaceVariant
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Bağlan", color = if (!isConnecting && ip.isNotBlank()) Color.Black else OnSurfaceDim, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(24.dp))

            // Instructions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceVariant.copy(alpha = 0.5f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Nasıl bağlanılır?", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Cyan)
                Text("1. TV Ayarlar → Geliştirici Seçenekleri'ni aç", fontSize = 11.sp, color = OnSurfaceDim)
                Text("2. ADB / USB Hata Ayıklama'yı etkinleştir", fontSize = 11.sp, color = OnSurfaceDim)
                Text("3. TV IP adresini gir (Ayarlar → Ağ)", fontSize = 11.sp, color = OnSurfaceDim)
                Text("4. İlk bağlantıda TV'de yetkilendirmeyi onayla", fontSize = 11.sp, color = OnSurfaceDim)
            }
        }
    }
}
