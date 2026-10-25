package com.smdozk.tvremote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class AppPalette(
    val light: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val card: Color,
    val cyan: Color,
    val cyanGlow: Color,
    val purple: Color,
    val amber: Color,
    val powerRed: Color,
    val error: Color,
    val success: Color,
    val onSurface: Color,
    val onSurfaceDim: Color,
    val onAccent: Color,
    val gradientCyan: Brush,
    val gradientCyanPurple: Brush,
    val glass: Brush,
    val glassBorder: Brush,
    val key: Brush,
    val keyBorder: Brush,
    val pad: Brush,
    val row: Brush,
    val terminalRow: Brush,
    val terminalText: Color,
    val terminalDim: Color,
    val dpadDisc: List<Color>,
    val ink: Color,
    val inkStrength: Float,
    val shadow: Color,
    val ambientA: Color,
    val ambientB: Color
)

private val DarkPalette = AppPalette(
    light = false,
    background = Color(0xFF060612),
    surface = Color(0xFF0C0C1C),
    surfaceVariant = Color(0xFF1A1A33),
    card = Color(0xFF111125),
    cyan = Color(0xFF00E5FF),
    cyanGlow = Color(0x5500E5FF),
    purple = Color(0xFF7C4DFF),
    amber = Color(0xFFFFB74D),
    powerRed = Color(0xFFFF5370),
    error = Color(0xFFFF5252),
    success = Color(0xFF4ADE80),
    onSurface = Color(0xFFE6E6FF),
    onSurfaceDim = Color(0xFF8A8AB0),
    onAccent = Color(0xFF001B21),
    gradientCyan = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF00B0FF))),
    gradientCyanPurple = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF))),
    glass = Brush.verticalGradient(listOf(Color(0xFF1A1A38), Color(0xFF0F0F24))),
    glassBorder = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.02f))),
    key = Brush.verticalGradient(listOf(Color(0xFF26264D), Color(0xFF15152E))),
    keyBorder = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.03f))),
    pad = Brush.verticalGradient(listOf(Color(0xFF1B1B3C), Color(0xFF0D0D21))),
    row = Brush.linearGradient(listOf(Color(0xFF1A1A38), Color(0xFF111125))),
    terminalRow = Brush.linearGradient(listOf(Color(0xFF161628), Color(0xFF111122))),
    terminalText = Color(0xFFB4B4D8),
    terminalDim = Color(0xFF9E9EC8),
    dpadDisc = listOf(Color(0xFF242450), Color(0xFF14142E), Color(0xFF0E0E22)),
    ink = Color.White,
    inkStrength = 1f,
    shadow = Color.Transparent,
    ambientA = Color(0xFF00E5FF).copy(alpha = 0.16f),
    ambientB = Color(0xFF7C4DFF).copy(alpha = 0.15f)
)

// Bright and saturated: white surfaces, electric blue to violet accents, soft violet-tinted shadows.
private val LightPalette = AppPalette(
    light = true,
    background = Color(0xFFF2F4FF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE2E6FA),
    card = Color(0xFFFFFFFF),
    cyan = Color(0xFF0078FF),
    cyanGlow = Color(0x550078FF),
    purple = Color(0xFF7C3AED),
    amber = Color(0xFFFF8A00),
    powerRed = Color(0xFFFF2D55),
    error = Color(0xFFE5294B),
    success = Color(0xFF12B76A),
    onSurface = Color(0xFF14172E),
    onSurfaceDim = Color(0xFF5D6388),
    onAccent = Color.White,
    gradientCyan = Brush.linearGradient(listOf(Color(0xFF00C6FF), Color(0xFF0066FF))),
    gradientCyanPurple = Brush.linearGradient(listOf(Color(0xFF00B4FF), Color(0xFF6A5BFF), Color(0xFFC23BFF))),
    glass = Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF8F9FF))),
    glassBorder = Brush.verticalGradient(listOf(Color(0xFFDCE2FF), Color(0xFFEDEFFF))),
    key = Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFECEFFF))),
    keyBorder = Brush.verticalGradient(listOf(Color(0xFFD6DCFF), Color(0xFFE6E9FF))),
    pad = Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE8EDFF))),
    row = Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF3F5FF))),
    terminalRow = Brush.linearGradient(listOf(Color(0xFFF7F8FF), Color(0xFFEEF1FF))),
    terminalText = Color(0xFF353A63),
    terminalDim = Color(0xFF666C95),
    dpadDisc = listOf(Color(0xFFFFFFFF), Color(0xFFF2F4FF), Color(0xFFE0E5FF)),
    ink = Color(0xFF1B1F4A),
    inkStrength = 1.8f,
    shadow = Color(0xFF4B4FB8).copy(alpha = 0.22f),
    ambientA = Color(0xFF00C6FF).copy(alpha = 0.30f),
    ambientB = Color(0xFFB44DFF).copy(alpha = 0.24f)
)

object ThemeState {
    var light by mutableStateOf(true)
}

// Read through state, so anything drawn with these colors follows a theme switch.
val Palette: AppPalette get() = if (ThemeState.light) LightPalette else DarkPalette

val Background: Color get() = Palette.background
val Surface: Color get() = Palette.surface
val SurfaceVariant: Color get() = Palette.surfaceVariant
val CardColor: Color get() = Palette.card

val Cyan: Color get() = Palette.cyan
val CyanGlow: Color get() = Palette.cyanGlow
val Purple: Color get() = Palette.purple
val Amber: Color get() = Palette.amber
val PowerRed: Color get() = Palette.powerRed
val ErrorColor: Color get() = Palette.error
val SuccessGreen: Color get() = Palette.success
val OnSurface: Color get() = Palette.onSurface
val OnSurfaceDim: Color get() = Palette.onSurfaceDim
val OnAccent: Color get() = Palette.onAccent

val GradientCyan: Brush get() = Palette.gradientCyan
val GradientCyanPurple: Brush get() = Palette.gradientCyanPurple
val GlassBrush: Brush get() = Palette.glass
val GlassBorderBrush: Brush get() = Palette.glassBorder
val KeyBrush: Brush get() = Palette.key
val KeyBorderBrush: Brush get() = Palette.keyBorder
val PadBrush: Brush get() = Palette.pad
val RowBrush: Brush get() = Palette.row
val TerminalRowBrush: Brush get() = Palette.terminalRow
val TerminalText: Color get() = Palette.terminalText
val TerminalDim: Color get() = Palette.terminalDim
val ShadowColor: Color get() = Palette.shadow

// Light theme only: lifts a surface off the page with a violet-tinted shadow.
fun Modifier.softShadow(shape: Shape, elevation: Dp = 4.dp): Modifier {
    val shadow = Palette.shadow
    return if (shadow.alpha > 0f) shadow(elevation, shape, clip = false, ambientColor = shadow, spotColor = shadow) else this
}

// Hairlines and faint fills: white on the dark theme, ink (a bit stronger) on the light one.
fun Ink(alpha: Float): Color = Palette.ink.copy(alpha = (alpha * Palette.inkStrength).coerceAtMost(1f))

private val DarkColorScheme = darkColorScheme(
    primary = DarkPalette.cyan,
    onPrimary = DarkPalette.onAccent,
    secondary = DarkPalette.purple,
    background = DarkPalette.background,
    onBackground = DarkPalette.onSurface,
    surface = DarkPalette.surface,
    onSurface = DarkPalette.onSurface,
    surfaceVariant = DarkPalette.surfaceVariant,
    onSurfaceVariant = DarkPalette.onSurfaceDim,
    surfaceContainer = DarkPalette.surface,
    outline = DarkPalette.surfaceVariant,
    error = DarkPalette.error
)

private val LightColorScheme = lightColorScheme(
    primary = LightPalette.cyan,
    onPrimary = LightPalette.onAccent,
    secondary = LightPalette.purple,
    background = LightPalette.background,
    onBackground = LightPalette.onSurface,
    surface = LightPalette.surface,
    onSurface = LightPalette.onSurface,
    surfaceVariant = LightPalette.surfaceVariant,
    onSurfaceVariant = LightPalette.onSurfaceDim,
    surfaceContainer = LightPalette.surface,
    surfaceContainerHigh = LightPalette.surface,
    outline = Color(0xFFC9CFF0),
    error = LightPalette.error
)

@Composable
fun TvRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (ThemeState.light) LightColorScheme else DarkColorScheme, content = content)
}
