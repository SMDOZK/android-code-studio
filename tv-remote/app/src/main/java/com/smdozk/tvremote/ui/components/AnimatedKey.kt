package com.smdozk.tvremote.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smdozk.tvremote.ui.theme.Cyan
import com.smdozk.tvremote.ui.theme.GlassBorderBrush
import com.smdozk.tvremote.ui.theme.GlassBrush
import com.smdozk.tvremote.ui.theme.GradientCyan
import com.smdozk.tvremote.ui.theme.KeyBorderBrush
import com.smdozk.tvremote.ui.theme.KeyBrush
import com.smdozk.tvremote.ui.theme.OnAccent
import com.smdozk.tvremote.ui.theme.OnSurface
import com.smdozk.tvremote.ui.theme.OnSurfaceDim

private const val PRESSED_SCALE_DELTA = 0.08f

@Composable
fun AnimatedKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    background: Brush = KeyBrush,
    glowColor: Color = Cyan,
    restGlow: Float = 0f,
    halo: Boolean = true,
    enabled: Boolean = true,
    repeatOnHold: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val press = remember { Animatable(0f) }
    Box(
        modifier = modifier
            // Own layer, so a press only re-records this key instead of its parent.
            .graphicsLayer()
            .keyPress(press, enabled, repeatOnHold, onClick)
            .drawBehind {
                if (!halo) return@drawBehind
                val glow = (restGlow + 0.6f * press.value).coerceIn(0f, 1f)
                if (glow < 0.01f) return@drawBehind
                val radius = size.minDimension * 0.8f
                val color = glowColor.copy(alpha = glow * 0.6f)
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to color,
                        0.62f to color,
                        1f to Color.Transparent,
                        center = center,
                        radius = radius
                    ),
                    radius = radius
                )
            }
            .graphicsLayer {
                val scale = 1f - PRESSED_SCALE_DELTA * press.value
                scaleX = scale
                scaleY = scale
                this.shape = shape
                clip = true
            }
            .background(background)
            .drawBehind {
                val p = press.value.coerceIn(0f, 1f)
                if (p < 0.01f) return@drawBehind
                drawRect(
                    Brush.radialGradient(
                        listOf(glowColor.copy(alpha = 0.38f * p), Color.Transparent),
                        center = center,
                        radius = size.maxDimension * 0.7f
                    )
                )
            }
            .border(1.dp, KeyBorderBrush, shape),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun AnimatedIconKey(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    iconSize: Dp = 24.dp,
    tint: Color = OnSurface,
    glowColor: Color = Cyan,
    showLabel: Boolean = true,
    repeatOnHold: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AnimatedKey(
            onClick = onClick,
            modifier = Modifier.size(size),
            glowColor = glowColor,
            repeatOnHold = repeatOnHold
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(iconSize))
        }
        if (showLabel) {
            Text(
                label,
                fontSize = 10.sp,
                color = OnSurfaceDim,
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
    }
}

@Composable
fun AnimatedTextKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 20.sp,
    tint: Color = OnSurface
) {
    AnimatedKey(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        halo = false
    ) {
        Text(label, fontSize = fontSize, fontWeight = FontWeight.Medium, color = tint)
    }
}

@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    AnimatedKey(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 54.dp)
            .alpha(if (enabled || loading) 1f else 0.45f),
        shape = RoundedCornerShape(18.dp),
        background = GradientCyan,
        glowColor = Color.White,
        halo = false,
        enabled = enabled && !loading
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when {
                loading -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = OnAccent,
                    strokeWidth = 2.dp
                )
                icon != null -> Icon(icon, contentDescription = null, tint = OnAccent, modifier = Modifier.size(20.dp))
            }
            Text(text, color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .background(GlassBrush, shape)
            .border(1.dp, GlassBorderBrush, shape),
        content = content
    )
}
