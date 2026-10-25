package com.smdozk.tvremote.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smdozk.tvremote.ui.theme.Cyan
import com.smdozk.tvremote.ui.theme.KeyBorderBrush
import com.smdozk.tvremote.ui.theme.KeyBrush
import com.smdozk.tvremote.ui.theme.OnSurface
import com.smdozk.tvremote.ui.theme.OnSurfaceDim
import com.smdozk.tvremote.ui.theme.softShadow

private val CapsuleShape = RoundedCornerShape(percent = 50)

@Composable
fun RockerKey(
    label: String,
    upIcon: ImageVector,
    upDescription: String,
    onUp: () -> Unit,
    downIcon: ImageVector,
    downDescription: String,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null
) {
    Column(
        modifier = modifier
            .width(72.dp)
            .height(196.dp)
            .softShadow(CapsuleShape)
            .clip(CapsuleShape)
            .background(KeyBrush)
            .border(1.dp, KeyBorderBrush, CapsuleShape),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RockerHalf(upIcon, upDescription, onUp, Modifier.weight(1f))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            color = OnSurfaceDim,
            modifier = Modifier.clearAndSetSemantics { }
        )
        if (caption != null) {
            Text(caption, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Cyan)
        }
        RockerHalf(downIcon, downDescription, onDown, Modifier.weight(1f))
    }
}

@Composable
private fun RockerHalf(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier) {
    val press = remember { Animatable(0f) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer()
            .keyPress(press, repeatOnHold = true, onClick = onClick)
            .drawBehind {
                val p = press.value.coerceIn(0f, 1f)
                if (p < 0.01f) return@drawBehind
                drawRect(
                    Brush.radialGradient(
                        listOf(Cyan.copy(alpha = 0.34f * p), Color.Transparent),
                        center = center,
                        radius = size.width * 0.9f
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = OnSurface,
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer {
                    val scale = 1f - 0.15f * press.value
                    scaleX = scale
                    scaleY = scale
                }
        )
    }
}
