package com.smdozk.tvremote.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smdozk.tvremote.adb.TvKeyCode
import com.smdozk.tvremote.ui.components.AnimatedKey
import com.smdozk.tvremote.ui.theme.Cyan
import com.smdozk.tvremote.ui.theme.GradientCyanPurple
import com.smdozk.tvremote.ui.theme.KeyBrush
import com.smdozk.tvremote.ui.theme.OnAccent
import com.smdozk.tvremote.ui.theme.OnSurface
import com.smdozk.tvremote.ui.theme.OnSurfaceDim
import com.smdozk.tvremote.ui.theme.Purple
import com.smdozk.tvremote.viewmodel.MainViewModel
import kotlin.math.abs

private val PadShape = RoundedCornerShape(28.dp)

// Magic Remote style cursor: drag moves, tap clicks, two fingers scroll.
@Composable
fun TouchpadScreen(vm: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Touchpad(
            onMove = vm::pointerMove,
            onScroll = vm::pointerScroll,
            onTap = vm::pointerClick,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PadKey(Icons.AutoMirrored.Filled.ArrowBack, "Geri") { vm.sendKey(TvKeyCode.BACK) }
            PadKey(Icons.Default.Home, "Ana Ekran", accent = true) { vm.sendKey(TvKeyCode.HOME) }
            PadKey(Icons.Default.Tune, "Ayar") { vm.sendButton("QMENU") }
        }
    }
}

@Composable
private fun Touchpad(
    onMove: (Float, Float) -> Unit,
    onScroll: (Float, Float) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Read only while drawing, so finger tracking never recomposes the screen.
    var touch by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = modifier
            .clip(PadShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1B3C), Color(0xFF0D0D21))))
            .border(1.dp, Brush.verticalGradient(listOf(Cyan.copy(alpha = 0.35f), Purple.copy(alpha = 0.15f))), PadShape)
            .semantics { contentDescription = "Dokunmatik fare alanı" }
            .graphicsLayer()
            .drawBehind {
                val point = touch ?: return@drawBehind
                val radius = 90.dp.toPx()
                drawCircle(Brush.radialGradient(listOf(Cyan.copy(alpha = 0.35f), Color.Transparent), point, radius), radius, point)
                drawCircle(Cyan.copy(alpha = 0.9f), 6.dp.toPx(), point)
            }
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downTime = down.uptimeMillis
                    var travelled = 0f
                    var multiTouch = false
                    var lastCentroid: Offset? = null
                    var upTime = downTime
                    touch = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        event.changes.firstOrNull()?.let { upTime = it.uptimeMillis }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            multiTouch = true
                            val centroid = pressed.fold(Offset.Zero) { sum, change -> sum + change.position } / pressed.size.toFloat()
                            lastCentroid?.let { previous ->
                                val delta = (centroid - previous) / density
                                // Natural scrolling: fingers up moves the page up, like a phone.
                                onScroll(-delta.x, -delta.y)
                            }
                            lastCentroid = centroid
                            touch = centroid
                        } else if (!multiTouch) {
                            val change = pressed.first()
                            val delta = change.position - change.previousPosition
                            travelled += delta.getDistance()
                            if (travelled > slop) {
                                val dp = delta / density
                                // Pointer acceleration: slow drags are precise, flicks cross the screen.
                                val gain = (1.4f + 0.08f * (abs(dp.x) + abs(dp.y))).coerceAtMost(4.5f)
                                onMove(dp.x * gain, dp.y * gain)
                            }
                            touch = change.position
                        } else {
                            lastCentroid = null
                        }
                        event.changes.forEach { it.consume() }
                    }
                    if (!multiTouch && travelled <= slop && upTime - downTime < TAP_TIMEOUT_MS) {
                        onTap()
                    }
                    touch = null
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Mouse, contentDescription = null, tint = Cyan.copy(alpha = 0.6f), modifier = Modifier.size(40.dp))
            Text("Dokunmatik fare", color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(
                "Kaydır: imleç  ·  Dokun: tıkla\nİki parmak: sayfayı kaydır",
                color = OnSurfaceDim,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RowScope.PadKey(icon: ImageVector, label: String, accent: Boolean = false, onClick: () -> Unit) {
    val content = if (accent) OnAccent else OnSurface
    AnimatedKey(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(54.dp),
        shape = RoundedCornerShape(percent = 50),
        background = if (accent) GradientCyanPurple else KeyBrush,
        glowColor = if (accent) Color.White else Cyan,
        halo = false
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Text(label, color = content, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

private const val TAP_TIMEOUT_MS = 280L
