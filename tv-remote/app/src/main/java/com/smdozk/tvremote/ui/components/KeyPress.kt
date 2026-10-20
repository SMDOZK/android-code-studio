package com.smdozk.tvremote.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val PressInSpec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 2200f)
internal val ReleaseSpec = spring<Float>(dampingRatio = 0.42f, stiffness = 650f)

internal const val REPEAT_DELAY_MS = 380L
internal const val REPEAT_INTERVAL_MS = 110L

// Press visuals start on touch-down, but the key fires on release so a scroll that starts on a key never sends it.
fun Modifier.keyPress(
    press: Animatable<Float, AnimationVector1D>,
    enabled: Boolean = true,
    repeatOnHold: Boolean = false,
    onClick: () -> Unit
): Modifier = composed {
    val currentOnClick by rememberUpdatedState(onClick)
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val fire = { haptic: Int ->
        view.performHapticFeedback(haptic)
        currentOnClick()
    }
    semantics(mergeDescendants = true) {
        role = Role.Button
        onClick { if (enabled) fire(HapticFeedbackConstants.VIRTUAL_KEY); enabled }
        if (!enabled) disabled()
    }.pointerInput(enabled, repeatOnHold) {
        if (!enabled) return@pointerInput
        detectTapGestures(onPress = {
            scope.launch { press.animateTo(1f, PressInSpec) }
            var repeated = false
            val repeater = if (repeatOnHold) scope.launch {
                delay(REPEAT_DELAY_MS)
                while (true) {
                    repeated = true
                    fire(HapticFeedbackConstants.CLOCK_TICK)
                    delay(REPEAT_INTERVAL_MS)
                }
            } else null
            val released = try {
                tryAwaitRelease()
            } finally {
                repeater?.cancel()
                scope.launch { press.animateTo(0f, ReleaseSpec) }
            }
            if (released && !repeated) fire(HapticFeedbackConstants.VIRTUAL_KEY)
        })
    }
}
