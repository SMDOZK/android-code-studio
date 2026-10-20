package com.smdozk.tvremote.ui.components

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smdozk.tvremote.ui.theme.Cyan
import com.smdozk.tvremote.ui.theme.OnSurfaceDim
import kotlin.math.roundToInt

@Composable
fun rememberDisplayRefreshRate(): Float {
    val view = LocalView.current
    var rate by remember { mutableFloatStateOf(view.display?.refreshRate ?: 60f) }
    DisposableEffect(view) {
        val displayManager = view.context.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                val display = view.display ?: return
                if (display.displayId == displayId) rate = display.refreshRate
            }
        }
        displayManager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        view.display?.let { rate = it.refreshRate }
        onDispose { displayManager.unregisterDisplayListener(listener) }
    }
    return rate
}

@Composable
fun RefreshRateBadge(modifier: Modifier = Modifier) {
    val hz = rememberDisplayRefreshRate().roundToInt()
    val high = hz >= 90
    Text(
        text = "$hz Hz",
        modifier = modifier
            .border(1.dp, if (high) Cyan.copy(alpha = 0.6f) else OnSurfaceDim.copy(alpha = 0.4f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = if (high) Cyan else OnSurfaceDim,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold
    )
}
