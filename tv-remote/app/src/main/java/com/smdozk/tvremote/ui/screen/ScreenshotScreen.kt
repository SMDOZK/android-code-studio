package com.smdozk.tvremote.ui.screen

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smdozk.tvremote.ui.components.AnimatedKey
import com.smdozk.tvremote.ui.components.GlassCard
import com.smdozk.tvremote.ui.components.GlowButton
import com.smdozk.tvremote.ui.theme.Cyan
import com.smdozk.tvremote.ui.theme.ErrorColor
import com.smdozk.tvremote.ui.theme.OnSurface
import com.smdozk.tvremote.ui.theme.OnSurfaceDim
import com.smdozk.tvremote.ui.theme.SurfaceVariant
import com.smdozk.tvremote.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

@Composable
fun ScreenshotScreen(vm: MainViewModel) {
    val screenshot by vm.screenshot.collectAsState()
    val isCapturing by vm.isCapturing.collectAsState()
    val captureError by vm.screenshotError.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var savedMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(savedMessage) {
        if (savedMessage != null) {
            delay(3000)
            savedMessage = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Ekran Görüntüsü", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
                Text(
                    screenshot?.let { "${it.width}×${it.height} px" } ?: "TV ekranını anında yakala",
                    fontSize = 12.sp,
                    color = OnSurfaceDim
                )
            }
            screenshot?.let { bitmap ->
                AnimatedKey(
                    onClick = {
                        isSaving = true
                        scope.launch {
                            savedMessage = saveBitmapToGallery(context, bitmap)
                            isSaving = false
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(color = Cyan, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Default.Save, contentDescription = "Galeriye kaydet", tint = Cyan, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.size(10.dp))
            }
            GlowButton(
                text = "Yakala",
                onClick = vm::captureScreenshot,
                icon = Icons.Default.CameraAlt,
                loading = isCapturing
            )
        }

        val message = savedMessage ?: captureError?.let { "Hata: $it" }
        AnimatedVisibility(visible = message != null, enter = fadeIn(), exit = fadeOut()) {
            val isError = message?.startsWith("Hata") == true
            Text(
                text = message.orEmpty(),
                fontSize = 12.sp,
                color = if (isError) ErrorColor else Cyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .background((if (isError) ErrorColor else Cyan).copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            )
        }

        GlassCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Crossfade(targetState = screenshot, label = "screenshot", modifier = Modifier.fillMaxSize()) { bitmap ->
                Box(modifier = Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "TV ekran görüntüsü",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(
                                modifier = Modifier.size(84.dp).background(SurfaceVariant, RoundedCornerShape(24.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Tv, contentDescription = null, tint = OnSurfaceDim, modifier = Modifier.size(40.dp))
                            }
                            Text("\"Yakala\" ile TV ekranını al", color = OnSurfaceDim, fontSize = 14.sp)
                        }
                    }
                    if (isCapturing) {
                        CircularProgressIndicator(color = Cyan, strokeWidth = 3.dp, modifier = Modifier.size(52.dp))
                    }
                }
            }
        }
    }
}

// PNG-encoding a 4K frame takes ~1s, so it must stay off the main thread.
private suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
    try {
        val filename = "tv_screenshot_${System.currentTimeMillis()}.png"
        val outputStream: OutputStream? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TVRemote")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let { context.contentResolver.openOutputStream(it) }
        } else {
            @Suppress("DEPRECATION")
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            java.io.FileOutputStream(java.io.File(dir, filename))
        }

        if (outputStream == null) return@withContext "Hata: galeri dosyası oluşturulamadı"
        outputStream.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        "Galeriye kaydedildi: $filename"
    } catch (e: Exception) {
        "Hata: ${e.message}"
    }
}
