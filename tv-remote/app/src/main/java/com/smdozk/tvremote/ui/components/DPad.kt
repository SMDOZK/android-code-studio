package com.smdozk.tvremote.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smdozk.tvremote.adb.TvKeyCode
import com.smdozk.tvremote.ui.theme.Cyan
import com.smdozk.tvremote.ui.theme.GradientCyan
import com.smdozk.tvremote.ui.theme.OnAccent
import com.smdozk.tvremote.ui.theme.OnSurfaceDim
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Declared in drawArc angle order (0° = right, clockwise) so the hit test can index by quadrant.
private enum class DPadDirection(val keyCode: Int, val angle: Float, val label: String) {
    Right(TvKeyCode.DPAD_RIGHT, 0f, "Sağ"),
    Down(TvKeyCode.DPAD_DOWN, 90f, "Aşağı"),
    Left(TvKeyCode.DPAD_LEFT, 180f, "Sol"),
    Up(TvKeyCode.DPAD_UP, 270f, "Yukarı"),
}

private const val DISC_RATIO = 0.94f
private const val INNER_RATIO = 0.40f
private val GlowReleaseSpec = spring<Float>(dampingRatio = 1f, stiffness = 260f)

private fun hitDirection(position: Offset, size: IntSize): DPadDirection? {
    val dx = position.x - size.width / 2f
    val dy = position.y - size.height / 2f
    val distance = sqrt(dx * dx + dy * dy)
    val outer = minOf(size.width, size.height) / 2f * DISC_RATIO
    if (distance > outer || distance < outer * INNER_RATIO) return null
    val degrees = (Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0
    return DPadDirection.entries[((degrees + 45.0) / 90.0).toInt() % 4]
}

@Composable
fun DPad(onKey: (Int) -> Unit, modifier: Modifier = Modifier) {
    val currentOnKey by rememberUpdatedState(onKey)
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val glows = remember { DPadDirection.entries.map { Animatable(0f) } }

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer()
                .semantics {
                    contentDescription = "Yön tuşları"
                    customActions = DPadDirection.entries.map { dir ->
                        CustomAccessibilityAction(dir.label) { currentOnKey(dir.keyCode); true }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onPress = press@{ position ->
                        val dir = hitDirection(position, size) ?: return@press
                        val glow = glows[dir.ordinal]
                        val fire = { haptic: Int ->
                            view.performHapticFeedback(haptic)
                            currentOnKey(dir.keyCode)
                        }
                        scope.launch { glow.animateTo(1f, PressInSpec) }
                        var repeated = false
                        val repeater = scope.launch {
                            delay(REPEAT_DELAY_MS)
                            while (true) {
                                repeated = true
                                fire(HapticFeedbackConstants.CLOCK_TICK)
                                delay(REPEAT_INTERVAL_MS)
                            }
                        }
                        val released = try {
                            tryAwaitRelease()
                        } finally {
                            repeater.cancel()
                            scope.launch { glow.animateTo(0f, GlowReleaseSpec) }
                        }
                        if (released && !repeated) fire(HapticFeedbackConstants.VIRTUAL_KEY)
                    })
                }
                .drawWithCache {
                    val c = size.center
                    val outer = size.minDimension / 2f * DISC_RATIO
                    val inner = outer * INNER_RATIO
                    val hairline = 1.dp.toPx()
                    val bloom = Brush.radialGradient(
                        (DISC_RATIO * 0.9f) to Cyan.copy(alpha = 0.10f),
                        1f to Color.Transparent,
                        center = c,
                        radius = size.minDimension / 2f
                    )
                    val disc = Brush.radialGradient(
                        listOf(Color(0xFF242450), Color(0xFF14142E), Color(0xFF0E0E22)),
                        center = c,
                        radius = outer
                    )
                    val segmentGlow = Brush.radialGradient(
                        INNER_RATIO to Color.Transparent,
                        0.78f to Cyan.copy(alpha = 0.42f),
                        1f to Cyan.copy(alpha = 0.10f),
                        center = c,
                        radius = outer
                    )
                    val discTopLeft = Offset(c.x - outer, c.y - outer)
                    val discSize = Size(outer * 2f, outer * 2f)
                    val rimInset = 5.dp.toPx()
                    val rimTopLeft = Offset(c.x - outer + rimInset, c.y - outer + rimInset)
                    val rimSize = Size((outer - rimInset) * 2f, (outer - rimInset) * 2f)
                    val rimStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    val chevronStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val chevronW = 8.dp.toPx()
                    val chevronH = 4.5.dp.toPx()
                    val chevron = Path().apply {
                        moveTo(-chevronW, chevronH)
                        lineTo(0f, -chevronH)
                        lineTo(chevronW, chevronH)
                    }
                    val chevronRadius = (inner + outer) / 2f
                    val nudge = 4.dp.toPx()
                    val dividerStart = inner + 8.dp.toPx()
                    val dividerEnd = outer - 10.dp.toPx()

                    onDrawBehind {
                        drawCircle(bloom, radius = size.minDimension / 2f, center = c)
                        drawCircle(disc, radius = outer, center = c)
                        drawCircle(Color.White.copy(alpha = 0.08f), radius = outer - hairline / 2f, center = c, style = Stroke(hairline))
                        drawCircle(Cyan.copy(alpha = 0.10f), radius = inner, center = c, style = Stroke(hairline))
                        for (k in 0 until 4) {
                            val a = Math.toRadians(45.0 + 90.0 * k)
                            val unit = Offset(cos(a).toFloat(), sin(a).toFloat())
                            drawLine(Color.White.copy(alpha = 0.05f), c + unit * dividerStart, c + unit * dividerEnd, hairline)
                        }
                        DPadDirection.entries.forEach { dir ->
                            val g = glows[dir.ordinal].value.coerceIn(0f, 1f)
                            if (g > 0.01f) {
                                drawArc(
                                    brush = segmentGlow,
                                    startAngle = dir.angle - 45f,
                                    sweepAngle = 90f,
                                    useCenter = true,
                                    topLeft = discTopLeft,
                                    size = discSize,
                                    alpha = g
                                )
                                drawArc(
                                    color = Cyan,
                                    startAngle = dir.angle - 26f,
                                    sweepAngle = 52f,
                                    useCenter = false,
                                    topLeft = rimTopLeft,
                                    size = rimSize,
                                    alpha = g,
                                    style = rimStroke
                                )
                            }
                            rotate(dir.angle + 90f, pivot = c) {
                                translate(c.x, c.y - chevronRadius - nudge * g) {
                                    drawPath(chevron, lerp(OnSurfaceDim, Cyan, g), style = chevronStroke)
                                }
                            }
                        }
                    }
                }
        )
        AnimatedKey(
            onClick = { currentOnKey(TvKeyCode.DPAD_CENTER) },
            modifier = Modifier.fillMaxSize(INNER_RATIO * DISC_RATIO * 0.9f),
            background = GradientCyan,
            restGlow = 0.25f
        ) {
            Text("OK", fontWeight = FontWeight.Bold, color = OnAccent, fontSize = 20.sp)
        }
    }
}
