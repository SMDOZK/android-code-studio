package com.smdozk.tvremote.ui.screen

import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.smdozk.tvremote.adb.MirrorState
import com.smdozk.tvremote.adb.TvKeyCode
import com.smdozk.tvremote.ui.components.AnimatedIconKey
import com.smdozk.tvremote.ui.components.AnimatedKey
import com.smdozk.tvremote.ui.components.DPad
import com.smdozk.tvremote.ui.components.GlowButton
import com.smdozk.tvremote.ui.components.NoticeBanner
import com.smdozk.tvremote.ui.theme.Amber
import com.smdozk.tvremote.ui.theme.Cyan
import com.smdozk.tvremote.ui.theme.ErrorColor
import com.smdozk.tvremote.ui.theme.GlassBorderBrush
import com.smdozk.tvremote.ui.theme.GradientCyan
import com.smdozk.tvremote.ui.theme.KeyBrush
import com.smdozk.tvremote.ui.theme.OnAccent
import com.smdozk.tvremote.ui.theme.OnSurface
import com.smdozk.tvremote.ui.theme.OnSurfaceDim
import com.smdozk.tvremote.ui.theme.PowerRed
import com.smdozk.tvremote.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun LiveScreen(vm: MainViewModel) {
    val mirrorState by vm.mirror.state.collectAsState()
    val screenshot by vm.screenshot.collectAsState()
    val isCapturing by vm.isCapturing.collectAsState()
    val notice by vm.screenshotNotice.collectAsState()
    var live by rememberSaveable { mutableStateOf(true) }
    var touchControl by rememberSaveable { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val sendKey: (Int) -> Unit = remember(vm) { { code -> vm.sendKey(code) } }
    val videoShape = RoundedCornerShape(20.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("TV Ekranı", color = OnSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (touchControl && live) "Dokunmatik kontrol açık" else "Canlı yansıtma ve kontrol",
                    color = OnSurfaceDim,
                    fontSize = 12.sp
                )
            }
            StatusChip(live, mirrorState)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(videoShape)
                .background(Color.Black)
                .border(1.dp, GlassBorderBrush, videoShape),
            contentAlignment = Alignment.Center
        ) {
            if (live) {
                key(attempt) {
                    MirrorSurface(
                        onSurfaceReady = vm::startMirror,
                        onSurfaceGone = { after -> vm.mirror.stop(after) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (touchControl) {
                    TouchOverlay(onTap = vm::tapScreen, onSwipe = vm::swipeScreen, modifier = Modifier.fillMaxSize())
                }
                when (val state = mirrorState) {
                    MirrorState.Starting -> CircularProgressIndicator(color = Cyan, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
                    is MirrorState.Failed -> FailedOverlay(state.message) { attempt++ }
                    else -> Unit
                }
            } else {
                val bitmap = screenshot
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Son ekran görüntüsü",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Tv, contentDescription = null, tint = OnSurfaceDim, modifier = Modifier.size(40.dp))
                        Text("Yayın duraklatıldı", color = OnSurfaceDim, fontSize = 13.sp)
                    }
                }
            }
            if (isCapturing) {
                CircularProgressIndicator(color = Cyan, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlowButton(
                text = if (live) "Durdur" else "Canlı izle",
                onClick = { live = !live },
                modifier = Modifier.weight(1f),
                icon = if (live) Icons.Default.Stop else Icons.Default.PlayArrow
            )
            AnimatedKey(
                onClick = vm::captureScreenshot,
                modifier = Modifier.size(54.dp),
                enabled = !isCapturing
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Ekran görüntüsü al ve kaydet", tint = Cyan, modifier = Modifier.size(24.dp))
            }
            AnimatedKey(
                onClick = { touchControl = !touchControl },
                modifier = Modifier.size(54.dp),
                background = if (touchControl) GradientCyan else KeyBrush,
                enabled = live
            ) {
                Icon(
                    Icons.Default.TouchApp,
                    contentDescription = if (touchControl) "Dokunmatik kontrolü kapat" else "Dokunmatik kontrolü aç",
                    tint = if (touchControl) OnAccent else OnSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        NoticeBanner(notice, onDismiss = vm::clearScreenshotNotice)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedIconKey(Icons.AutoMirrored.Filled.ArrowBack, "Geri", { sendKey(TvKeyCode.BACK) })
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                DPad(onKey = sendKey, modifier = Modifier.widthIn(max = 230.dp).fillMaxWidth().padding(horizontal = 8.dp))
            }
            AnimatedIconKey(Icons.Default.Home, "Ana Ekran", { sendKey(TvKeyCode.HOME) })
        }

        Text(
            "Dokunmatik modda görüntüye dokunmak ya da kaydırmak TV'de aynısını yapar. " +
                "DRM korumalı içerik (ör. Netflix) siyah görünebilir.",
            color = OnSurfaceDim,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StatusChip(live: Boolean, state: MirrorState) {
    val (text, color) = when {
        !live -> "Duraklatıldı" to OnSurfaceDim
        state is MirrorState.Live -> (if (state.fps > 0) "CANLI · ${state.fps} fps" else "CANLI") to PowerRed
        state is MirrorState.Starting -> "Bağlanıyor…" to Amber
        state is MirrorState.Failed -> "Hata" to ErrorColor
        else -> "Hazırlanıyor" to OnSurfaceDim
    }
    Row(
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.5f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FailedOverlay(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, color = ErrorColor, fontSize = 13.sp, textAlign = TextAlign.Center)
        TextButton(onClick = onRetry) { Text("Tekrar dene", color = Cyan) }
    }
}

@Composable
private fun TouchOverlay(
    onTap: (Float, Float) -> Unit,
    onSwipe: (Float, Float, Float, Float, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val ripple = remember { Animatable(1f) }
    var ripplePosition by remember { mutableStateOf(Offset.Unspecified) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { position ->
                    onTap(position.x / size.width, position.y / size.height)
                    ripplePosition = position
                    scope.launch {
                        ripple.snapTo(0f)
                        ripple.animateTo(1f, tween(450))
                    }
                }
            }
            .pointerInput(Unit) {
                var start = Offset.Zero
                var last = Offset.Zero
                var startedAt = 0L
                detectDragGestures(
                    onDragStart = {
                        start = it
                        last = it
                        startedAt = SystemClock.uptimeMillis()
                    },
                    onDrag = { change, _ -> last = change.position },
                    onDragEnd = {
                        val duration = (SystemClock.uptimeMillis() - startedAt).coerceIn(80L, 1500L)
                        onSwipe(start.x / size.width, start.y / size.height, last.x / size.width, last.y / size.height, duration)
                    }
                )
            }
            .drawBehind {
                val progress = ripple.value
                if (progress < 1f && ripplePosition.isSpecified) {
                    drawCircle(
                        color = Cyan.copy(alpha = 0.6f * (1f - progress)),
                        radius = 10.dp.toPx() + 26.dp.toPx() * progress,
                        center = ripplePosition,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
    )
}

@Composable
private fun MirrorSurface(
    onSurfaceReady: (Surface) -> Unit,
    onSurfaceGone: ((() -> Unit)?) -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val host = remember { MirrorHost() }
    host.onReady = onSurfaceReady
    host.onGone = onSurfaceGone

    DisposableEffect(lifecycleOwner) {
        // Stream only while the app is visible; the TV keeps encoding otherwise.
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> host.resume()
                Lifecycle.Event.ON_STOP -> host.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            host.pause()
        }
    }

    AndroidView(
        factory = { context -> TextureView(context).apply { surfaceTextureListener = host } },
        modifier = modifier
    )
}

private class MirrorHost : TextureView.SurfaceTextureListener {
    var onReady: (Surface) -> Unit = {}
    var onGone: ((() -> Unit)?) -> Unit = {}

    private var surface: Surface? = null
    private var streaming = false
    private var visible = true

    fun resume() {
        visible = true
        start()
    }

    fun pause() {
        visible = false
        stop(null)
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        surface = Surface(texture)
        start()
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        val released = surface
        surface = null
        // The decoder may still be rendering; free the surface only after it has stopped.
        stop {
            released?.release()
            texture.release()
        }
        return false
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    private fun start() {
        val target = surface ?: return
        if (streaming || !visible) return
        streaming = true
        onReady(target)
    }

    private fun stop(after: (() -> Unit)?) {
        if (streaming) {
            streaming = false
            onGone(after)
        } else {
            after?.invoke()
        }
    }
}
