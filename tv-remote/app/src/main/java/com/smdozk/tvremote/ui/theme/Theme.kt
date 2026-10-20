package com.smdozk.tvremote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Background = Color(0xFF060612)
val Surface = Color(0xFF0E0E20)
val SurfaceVariant = Color(0xFF161628)
val CardColor = Color(0xFF111125)
val CardColorAlt = Color(0xFF18183A)

val Cyan = Color(0xFF00E5FF)
val CyanDim = Color(0xFF00BCD4)
val CyanGlow = Color(0x5500E5FF)
val Purple = Color(0xFF7C4DFF)
val PurpleGlow = Color(0x557C4DFF)
val ErrorColor = Color(0xFFFF5252)
val OnSurface = Color(0xFFE0E0FF)
val OnSurfaceDim = Color(0xFF8888AA)

val GradientCyan = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF00B8D4)))
val GradientPurple = Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFF448AFF)))
val GradientCyanPurple = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF)))
val GradientCard = Brush.linearGradient(
    listOf(Color(0xFF161632), Color(0xFF0E0E24))
)

private val DarkColorScheme = darkColorScheme(
    primary = Cyan,
    secondary = Purple,
    background = Background,
    surface = Surface,
    surfaceVariant = SurfaceVariant,
    onPrimary = Color(0xFF001F24),
    onBackground = OnSurface,
    onSurface = OnSurface,
    error = ErrorColor
)

@Composable
fun TvRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
