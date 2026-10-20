package com.smdozk.tvremote.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.smdozk.tvremote.ui.components.GlassCard
import com.smdozk.tvremote.ui.components.NoticeBanner
import com.smdozk.tvremote.ui.theme.Amber
import com.smdozk.tvremote.ui.theme.Cyan
import com.smdozk.tvremote.ui.theme.CyanGlow
import com.smdozk.tvremote.ui.theme.OnSurface
import com.smdozk.tvremote.ui.theme.OnSurfaceDim
import com.smdozk.tvremote.ui.theme.SuccessGreen
import com.smdozk.tvremote.viewmodel.DevState
import com.smdozk.tvremote.viewmodel.MainViewModel

// Developer Mode on the TV and the phone: status, a shortcut to each, and the auto-open switch.
@Composable
fun DeveloperCard(vm: MainViewModel, modifier: Modifier = Modifier) {
    val tvDev by vm.tvDev.collectAsState()
    val phoneDev by vm.phoneDev.collectAsState()
    val auto by vm.devAuto.collectAsState()
    val notice by vm.remoteNotice.collectAsState()

    // Coming back from the phone's settings or the TV app: re-read both states.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshDevStatus()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Geliştirici Modu", color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            DevRow(
                label = "TV",
                status = when (tvDev) {
                    DevState.On -> "Açık ✓"
                    DevState.Off -> "Kapalı"
                    DevState.Unknown -> "Kontrol ediliyor…"
                },
                statusColor = when (tvDev) {
                    DevState.On -> SuccessGreen
                    DevState.Off -> Amber
                    DevState.Unknown -> OnSurfaceDim
                },
                action = "TV'de aç",
                onAction = vm::openDeveloperMode
            )
            DevRow(
                label = "Telefon",
                status = if (phoneDev) "Açık ✓" else "Kapalı",
                statusColor = if (phoneDev) SuccessGreen else Amber,
                action = if (phoneDev) "Ayarlar" else "Aç",
                onAction = vm::openPhoneDeveloperSettings
            )
            if (!phoneDev) {
                Text(
                    "Açılan ekranda \"Yapı numarası\"na 7 kez dokun (Samsung: Yazılım bilgileri içinde).",
                    color = OnSurfaceDim,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bağlanınca otomatik aç", color = OnSurfaceDim, fontSize = 12.sp)
                Switch(
                    checked = auto,
                    onCheckedChange = vm::setDevAuto,
                    modifier = Modifier.height(24.dp),
                    colors = SwitchDefaults.colors(checkedThumbColor = Cyan, checkedTrackColor = CyanGlow)
                )
            }
            NoticeBanner(notice, onDismiss = vm::clearRemoteNotice, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun DevRow(label: String, status: String, statusColor: Color, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = OnSurface, fontSize = 13.sp, modifier = Modifier.padding(end = 2.dp))
            Text(status, color = statusColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        TextButton(onClick = onAction) { Text(action, color = Cyan) }
    }
}
