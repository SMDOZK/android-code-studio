package com.smdozk.tvremote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Background = Color(0xFF060612)
val Surface = Color(0xFF0C0C1C)
val SurfaceVariant = Color(0xFF1A1A33)
val CardColor = Color(0xFF111125)

val Cyan = Color(0xFF00E5FF)
val CyanGlow = Color(0x5500E5FF)
val Purple = Color(0xFF7C4DFF)
val Amber = Color(0xFFFFB74D)
val PowerRed = Color(0xFFFF5370)
val ErrorColor = Color(0xFFFF5252)
val SuccessGreen = Color(0xFF4ADE80)
val OnSurface = Color(0xFFE6E6FF)
val OnSurfaceDim = Color(0xFF8A8AB0)
val OnAccent = Color(0xFF001B21)

val GradientCyan = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF00B0FF)))
val GradientCyanPurple = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF)))
val GlassBrush = Brush.verticalGradient(listOf(Color(0xFF1A1A38), Color(0xFF0F0F24)))
val GlassBorderBrush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.02f)))
val KeyBrush = Brush.verticalGradient(listOf(Color(0xFF26264D), Color(0xFF15152E)))
val KeyBorderBrush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.03f)))

private val DarkColorScheme = darkColorScheme(
    primary = Cyan,
    onPrimary = OnAccent,
    secondary = Purple,
    background = Background,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceDim,
    surfaceContainer = Surface,
    outline = SurfaceVariant,
    error = ErrorColor
)

@Composable
fun TvRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, content = content)
}
