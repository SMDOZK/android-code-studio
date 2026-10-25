package com.smdozk.tvremote.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.smdozk.tvremote.ui.theme.Amber
import com.smdozk.tvremote.ui.theme.Purple
import com.smdozk.tvremote.ui.theme.ThemeState

@Composable
fun ThemeToggle(onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val light = ThemeState.light
    IconButton(onClick = { onChange(!light) }, modifier = modifier) {
        Crossfade(targetState = light, label = "theme") { isLight ->
            Icon(
                if (isLight) Icons.Default.DarkMode else Icons.Default.LightMode,
                contentDescription = if (isLight) "Koyu temaya geç" else "Açık temaya geç",
                tint = if (isLight) Purple else Amber
            )
        }
    }
}
