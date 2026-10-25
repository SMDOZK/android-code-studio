package com.smdozk.tvremote.ui.screen

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.smdozk.tvremote.adb.TvKeyCode
import com.smdozk.tvremote.input.AirPointer
import com.smdozk.tvremote.ui.components.AnimatedKey
import com.smdozk.tvremote.ui.theme.Amber
import com.smdozk.tvremote.ui.theme.Cyan
import com.smdozk.tvremote.ui.theme.GradientCyanPurple
import com.smdozk.tvremote.ui.theme.KeyBrush
import com.smdozk.tvremote.ui.theme.OnAccent
import com.smdozk.tvremote.ui.theme.OnSurface
import com.smdozk.tvremote.ui.theme.OnSurfaceDim
import com.smdozk.tvremote.ui.theme.Purple
import com.smdozk.tvremote.ui.theme.SuccessGreen
import com.smdozk.tvremote.ui.theme.SurfaceVariant
import com.smdozk.tvremote.ui.theme.PadBrush
import com.smdozk.tvremote.ui.theme.softShadow
import com.smdozk.tvremote.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs

private val PadShape = RoundedCornerShape(28.dp)

// Two ways to drive the TV cursor: drag on the pad, or point the phone at the TV like a Magic Remote.
@Composable
fun TouchpadScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val hasGyro = remember {
        (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager).getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
    }
    val gyroSetting by vm.gyroMode.collectAsState()
    val gyroMode = gyroSetting && hasGyro
    val padSpeed by vm.pointerSpeed.collectAsState()
    val gyroSpeed by vm.gyroSpeed.collectAsState()

    val pointer = remember { AirPointer(vm.gyroBias, vm::pointerMove) }
    SideEffect { pointer.speed = gyroSpeed }
    var calibration by remember { mutableStateOf<AirPointer.Calibration?>(null) }
    DisposableEffect(pointer) {
        pointer.onCalibration = { result ->
            calibration = result
            if (result is AirPointer.Calibration.Done) vm.setGyroBias(result.bias)
        }
        onDispose {
            pointer.cancelCalibration()
            pointer.onCalibration = {}
        }
    }
    LaunchedEffect(calibration) {
        if (calibration is AirPointer.Calibration.Done || calibration is AirPointer.Calibration.Failed) {
            delay(CALIBRATION_MESSAGE_MS)
            calibration = null
        }
    }
    if (gyroMode) AirPointerSensors(pointer)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (gyroMode) "Jiroskop fare" else "Dokunmatik fare",
                color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            if (gyroMode) {
                val measuring = calibration is AirPointer.Calibration.Measuring
                TextButton(onClick = { pointer.startCalibration() }, enabled = !measuring) {
                    Text(
                        "Kalibre et",
                        color = if (measuring) OnSurfaceDim else Cyan,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
            if (hasGyro) {
                IconButton(onClick = {
                    pointer.cancelCalibration()
                    calibration = null
                    vm.setGyroMode(!gyroMode)
                }) {
                    Icon(
                        if (gyroMode) Icons.Default.Mouse else Icons.Default.Explore,
                        contentDescription = if (gyroMode) "Dokunmatik moda geç" else "Jiroskop moduna geç",
                        tint = Cyan,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        if (gyroMode) {
            GyroscopePad(
                pointer = pointer,
                calibration = calibration,
                onScroll = vm::pointerScroll,
                onTap = vm::pointerClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            Touchpad(
                onMove = vm::pointerMove,
                onScroll = vm::pointerScroll,
                onTap = vm::pointerClick,
                speed = padSpeed,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
        val speed = if (gyroMode) gyroSpeed else padSpeed
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Hız", color = OnSurfaceDim, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Slider(
                value = speed,
                onValueChange = if (gyroMode) vm::setGyroSpeed else vm::setPointerSpeed,
                valueRange = 0.5f..3f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Cyan,
                    activeTrackColor = Cyan,
                    inactiveTrackColor = SurfaceVariant
                )
            )
            Text("%.1fx".format(speed), color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PadKey(Icons.AutoMirrored.Filled.ArrowBack, "Geri") { vm.sendKey(TvKeyCode.BACK) }
            PadKey(Icons.Default.Home, "Ana Ekran", accent = true) { vm.sendKey(TvKeyCode.HOME) }
            PadKey(Icons.Default.Settings, "Ayarlar") { vm.sendKey(TvKeyCode.SETTINGS) }
        }
    }
}

@Composable
private fun Touchpad(
    onMove: (Float, Float) -> Unit,
    onScroll: (Float, Float) -> Unit,
    onTap: () -> Unit,
    speed: Float,
    modifier: Modifier = Modifier
) {
    val currentSpeed by rememberUpdatedState(speed)
    // Read only while drawing, so finger tracking never recomposes the screen.
    var touch by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = modifier
            .softShadow(PadShape, 8.dp)
            .clip(PadShape)
            .background(PadBrush)
            .border(1.dp, Brush.verticalGradient(listOf(Cyan.copy(alpha = 0.35f), Purple.copy(alpha = 0.15f))), PadShape)
            .semantics { contentDescription = "Dokunmatik fare alanı" }
            .graphicsLayer()
            .drawBehind {
                val point = touch ?: return@drawBehind
                val radius = 90.dp.toPx()
                drawCircle(Brush.radialGradient(listOf(Cyan.copy(alpha = 0.35f), Color.Transparent), point, radius), radius, point)
                drawCircle(Cyan.copy(alpha = 0.9f), 6.dp.toPx(), point)
            }
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downTime = down.uptimeMillis
                    var travelled = 0f
                    var multiTouch = false
                    var lastCentroid: Offset? = null
                    var upTime = downTime
                    touch = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        event.changes.firstOrNull()?.let { upTime = it.uptimeMillis }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            multiTouch = true
                            val centroid = pressed.fold(Offset.Zero) { sum, change -> sum + change.position } / pressed.size.toFloat()
                            lastCentroid?.let { previous ->
                                val delta = (centroid - previous) / density
                                // Natural scrolling: fingers up moves the page up, like a phone.
                                onScroll(-delta.x, -delta.y)
                            }
                            lastCentroid = centroid
                            touch = centroid
                        } else if (!multiTouch) {
                            val change = pressed.first()
                            val delta = change.position - change.previousPosition
                            travelled += delta.getDistance()
                            if (travelled > slop) {
                                val dp = delta / density
                                // Pointer acceleration: slow drags are precise, flicks cross the screen.
                                val gain = currentSpeed * (2.4f + 0.12f * (abs(dp.x) + abs(dp.y))).coerceAtMost(7f)
                                onMove(dp.x * gain, dp.y * gain)
                            }
                            touch = change.position
                        } else {
                            lastCentroid = null
                        }
                        event.changes.forEach { it.consume() }
                    }
                    if (!multiTouch && travelled <= slop && upTime - downTime < TAP_TIMEOUT_MS) {
                        onTap()
                    }
                    touch = null
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Mouse, contentDescription = null, tint = Cyan.copy(alpha = 0.6f), modifier = Modifier.size(40.dp))
            Text("Dokunmatik fare", color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(
                "Kaydır: imleç  ·  Dokun: tıkla\nİki parmak: sayfayı kaydır",
                color = OnSurfaceDim,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Feeds the gyroscope and gravity sensors into the pointer while the app is in front.
@Composable
private fun AirPointerSensors(pointer: AirPointer) {
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(pointer, view, owner) {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val displays = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val gyro = sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val gravity = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        fun readRotation() {
            pointer.screenRotation = (view.display?.rotation ?: Surface.ROTATION_0) * 90
        }
        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayChanged(displayId: Int) = readRotation()
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val v = event.values
                when (event.sensor.type) {
                    Sensor.TYPE_GYROSCOPE -> pointer.onGyro(v[0], v[1], v[2], event.timestamp)
                    Sensor.TYPE_GRAVITY -> pointer.onGravity(v[0], v[1], v[2])
                    Sensor.TYPE_ACCELEROMETER -> pointer.onGravity(v[0], v[1], v[2], smooth = true)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        var running = false
        fun start() {
            if (running || gyro == null) return
            running = true
            readRotation()
            pointer.reset()
            gravity?.let { sensors.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
            sensors.registerListener(listener, gyro, GYRO_PERIOD_US)
            displays.registerDisplayListener(displayListener, null)
            // Pointing does not touch the screen, so it must not time out mid-use.
            view.keepScreenOn = true
        }
        fun stop() {
            if (!running) return
            running = false
            sensors.unregisterListener(listener)
            displays.unregisterDisplayListener(displayListener)
            view.keepScreenOn = false
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE -> stop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            stop()
        }
    }
}

@Composable
private fun GyroscopePad(
    pointer: AirPointer,
    calibration: AirPointer.Calibration?,
    onScroll: (Float, Float) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnScroll by rememberUpdatedState(onScroll)
    val currentOnTap by rememberUpdatedState(onTap)
    var touch by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = modifier
            .softShadow(PadShape, 8.dp)
            .clip(PadShape)
            .background(PadBrush)
            .border(1.dp, Brush.verticalGradient(listOf(Cyan.copy(alpha = 0.45f), Purple.copy(alpha = 0.25f))), PadShape)
            .semantics { contentDescription = "Jiroskop fare alanı" }
            .graphicsLayer()
            .drawBehind {
                val point = touch ?: return@drawBehind
                val radius = 90.dp.toPx()
                drawCircle(Brush.radialGradient(listOf(Purple.copy(alpha = 0.35f), Color.Transparent), point, radius), radius, point)
                drawCircle(Cyan.copy(alpha = 0.9f), 6.dp.toPx(), point)
            }
            .pointerInput(pointer) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Pressing the screen jolts the phone: freeze the cursor so the click lands where it was aimed.
                    pointer.hold(true)
                    touch = down.position
                    var travelled = 0f
                    var upTime = down.uptimeMillis
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.firstOrNull()?.let { upTime = it.uptimeMillis }
                            val change = event.changes.firstOrNull { it.pressed } ?: break
                            val delta = change.position - change.previousPosition
                            travelled += delta.getDistance()
                            if (travelled > slop) {
                                val d = delta / density
                                currentOnScroll(-d.x, -d.y)
                            }
                            touch = change.position
                            event.changes.forEach { it.consume() }
                        }
                    } finally {
                        pointer.hold(false)
                        touch = null
                    }
                    if (travelled <= slop && upTime - down.uptimeMillis < TAP_TIMEOUT_MS) currentOnTap()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            when (calibration) {
                is AirPointer.Calibration.Measuring -> {
                    Icon(Icons.Default.Explore, contentDescription = null, tint = Cyan, modifier = Modifier.size(44.dp))
                    Text("Kalibre ediliyor…", color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    PadHint("Telefonu düz bir yere bırak ve kıpırdatma")
                    LinearProgressIndicator(
                        progress = { calibration.progress },
                        modifier = Modifier.width(180.dp),
                        color = Cyan,
                        trackColor = SurfaceVariant
                    )
                }
                is AirPointer.Calibration.Done -> {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(44.dp))
                    Text("Kalibrasyon tamam", color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    PadHint("İmleç artık kendi kendine kaymaz")
                }
                AirPointer.Calibration.Failed -> {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Amber, modifier = Modifier.size(44.dp))
                    Text("Telefon kıpırdadı", color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    PadHint("Düz bir yere bırakıp tekrar dene")
                }
                null -> {
                    Icon(Icons.Default.Explore, contentDescription = null, tint = Cyan.copy(alpha = 0.7f), modifier = Modifier.size(44.dp))
                    Text("Telefonu TV'ye doğrult", color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    PadHint("Sağa-sola çevir, yukarı-aşağı eğ: imleç gider\nDokun: tıkla  ·  Basılı tut: imleç durur\nSürükle: sayfayı kaydır")
                }
            }
        }
    }
}

@Composable
private fun PadHint(text: String) {
    Text(text, color = OnSurfaceDim, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
}

@Composable
private fun RowScope.PadKey(icon: ImageVector, label: String, accent: Boolean = false, onClick: () -> Unit) {
    val content = if (accent) OnAccent else OnSurface
    AnimatedKey(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(54.dp),
        shape = RoundedCornerShape(percent = 50),
        background = if (accent) GradientCyanPurple else KeyBrush,
        glowColor = if (accent) Color.White else Cyan,
        halo = false
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Text(label, color = content, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

private const val TAP_TIMEOUT_MS = 280L
private const val GYRO_PERIOD_US = 10_000
private const val CALIBRATION_MESSAGE_MS = 2_500L
