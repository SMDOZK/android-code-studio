package com.smdozk.tvremote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Cyan = Color(0xFF00BCD4)
val CyanDark = Color(0xFF0097A7)
val Background = Color(0xFF0A0A0F)
val Surface = Color(0xFF13131F)
val SurfaceVariant = Color(0xFF1C1C2E)
val CardColor = Color(0xFF1E1E32)
val OnSurface = Color(0xFFE8E8FF)
val ErrorColor = Color(0xFFCF6679)

private val ColorScheme = darkColorScheme(
    primary = Cyan,
    onPrimary = Color(0xFF003640),
    primaryContainer = CyanDark,
    secondary = Color(0xFF90CAF9),
    onSecondary = Color(0xFF003060),
    error = ErrorColor,
    background = Background,
    surface = Surface,
    surfaceVariant = SurfaceVariant,
    onBackground = OnSurface,
    onSurface = OnSurface,
    onSurfaceVariant = Color(0xFFAAAAAA),
    outline = Color(0xFF444455),
)

@Composable
fun TVRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        content = content
    )
}
