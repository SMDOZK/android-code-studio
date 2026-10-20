package com.smdozk.tvremote.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smdozk.tvremote.model.ConnectionState
import com.smdozk.tvremote.ui.theme.Cyan
import com.smdozk.tvremote.ui.theme.ErrorColor
import com.smdozk.tvremote.ui.theme.SurfaceVariant
import com.smdozk.tvremote.viewmodel.MainViewModel

@Composable
fun ConnectScreen(vm: MainViewModel) {
    val state by vm.connectionState.collectAsState()
    val error by vm.connectionError.collectAsState()

    var ip by rememberSaveable { mutableStateOf("192.168.1.") }
    var port by rememberSaveable { mutableStateOf("5555") }

    val focusManager = LocalFocusManager.current
    val isConnecting = state == ConnectionState.CONNECTING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Tv,
            contentDescription = null,
            tint = Cyan,
            modifier = Modifier.size(72.dp)
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "TV Remote",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Cyan
        )

        Text(
            text = "Sunny TV · ADB WiFi",
            fontSize = 13.sp,
            color = Color.Gray
        )

        Spacer(Modifier.height(40.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Bağlantı Ayarları", fontWeight = FontWeight.SemiBold, color = Cyan)

                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("TV IP Adresi") },
                    placeholder = { Text("192.168.1.100") },
                    leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isConnecting
                )

                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (!isConnecting) {
                                vm.connect(ip, port.toIntOrNull() ?: 5555)
                            }
                        }
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isConnecting
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        AnimatedVisibility(visible = isConnecting) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Cyan, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    "Bağlanıyor... TV'de ADB yetkilendirme isteğini kabul edin.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }

        AnimatedVisibility(visible = error != null && !isConnecting) {
            Card(
                colors = CardDefaults.cardColors(containerColor = ErrorColor.copy(alpha = 0.15f)),
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

        Button(
            onClick = { vm.connect(ip, port.toIntOrNull() ?: 5555) },
            enabled = !isConnecting && ip.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Cyan)
        ) {
            Text("Bağlan", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Nasıl bağlanılır?", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Cyan)
                Text("1. TV'de Ayarlar → Cihaz Tercihleri → Geliştirici Seçenekleri'ni aç", fontSize = 11.sp, color = Color.Gray)
                Text("2. \"USB Üzerinden Hata Ayıklama\" veya \"ADB Hata Ayıklama\" seçeneğini etkinleştir", fontSize = 11.sp, color = Color.Gray)
                Text("3. TV'nin IP adresini gir (Ayarlar → Ağ)", fontSize = 11.sp, color = Color.Gray)
                Text("4. İlk bağlantıda TV ekranında yetkilendirme onayı görebilirsin", fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}
