package com.smdozk.tvremote.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import com.smdozk.tvremote.ui.theme.*

@Composable
fun AnimatedKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    backgroundBrush: Brush = Brush.radialGradient(listOf(SurfaceVariant, CardColor)),
    borderColor: Color = SurfaceVariant,
    glowColor: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit
) {
    var pressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 800f
        ),
        label = "scale"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.7f else 0.25f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 600f),
        label = "glow"
    )

    val elevation by animateDpAsState(
        targetValue = if (pressed) 0.dp else 6.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 800f),
        label = "elev"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(elevation, shape, spotColor = glowColor.copy(alpha = glowAlpha))
            .clip(shape)
            .background(backgroundBrush)
            .border(0.8.dp, borderColor.copy(alpha = 0.4f), shape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                        onClick()
                    }
                )
            },
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
    size: Dp = 52.dp,
    iconSize: Dp = 22.dp,
    tint: Color = Color.White,
    glowColor: Color = CyanGlow,
    showLabel: Boolean = true
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AnimatedKey(
            onClick = onClick,
            size = size,
            glowColor = glowColor,
            backgroundBrush = Brush.radialGradient(listOf(SurfaceVariant, CardColor))
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(iconSize))
        }
        if (showLabel) {
            Text(label, fontSize = 9.sp, color = OnSurfaceDim)
        }
    }
}

@Composable
fun AnimatedTextKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    fontSize: TextUnit = 18.sp,
    tint: Color = OnSurface
) {
    AnimatedKey(
        onClick = onClick,
        modifier = modifier,
        size = size,
        shape = RoundedCornerShape(12.dp),
        backgroundBrush = Brush.linearGradient(listOf(SurfaceVariant, CardColor)),
        glowColor = CyanGlow
    ) {
        Text(label, fontSize = fontSize, color = tint)
    }
}
