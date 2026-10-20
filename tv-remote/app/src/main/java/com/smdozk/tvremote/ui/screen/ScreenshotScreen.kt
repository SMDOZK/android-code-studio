package com.smdozk.tvremote.ui.screen

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smdozk.tvremote.ui.components.AnimatedKey
import com.smdozk.tvremote.ui.theme.*
import com.smdozk.tvremote.viewmodel.MainViewModel
import java.io.OutputStream

@Composable
fun ScreenshotScreen(vm: MainViewModel) {
    val screenshot by vm.screenshot.collectAsState()
    val isCapturing by vm.isCapturing.collectAsState()
    val context = LocalContext.current
    var savedMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Ekran Görüntüsü", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Cyan)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                screenshot?.let { bmp ->
                    AnimatedKey(
                        onClick = { savedMessage = saveBitmapToGallery(context, bmp) },
                        size = 44.dp,
                        backgroundBrush = Brush.radialGradient(listOf(SurfaceVariant, CardColor)),
                        glowColor = CyanGlow
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Kaydet", tint = Cyan, modifier = Modifier.size(20.dp))
                    }
                }
                Button(
                    onClick = vm::captureScreenshot,
                    enabled = !isCapturing,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan, disabledContainerColor = SurfaceVariant),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Cyan, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Yakala", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        savedMessage?.let { msg ->
            Spacer(Modifier.height(8.dp))
            val isError = msg.startsWith("Hata")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isError) ErrorColor.copy(alpha = 0.15f) else CyanGlow.copy(alpha = 0.2f))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(msg, fontSize = 12.sp, color = if (isError) ErrorColor else Cyan)
            }
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(3000)
                savedMessage = null
            }
        }

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0xFF1A1A38), Color(0xFF0E0E24)))
                )
                .border(0.8.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            when {
                isCapturing -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Cyan, modifier = Modifier.size(52.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.height(14.dp))
                    Text("TV ekranı alınıyor...", color = OnSurfaceDim)
                }
                screenshot != null -> Image(
                    bitmap = screenshot!!.asImageBitmap(),
                    contentDescription = "TV ekran görüntüsü",
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentScale = ContentScale.Fit
                )
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(SurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Tv, contentDescription = null, tint = OnSurfaceDim, modifier = Modifier.size(40.dp))
                    }
                    Text("\"Yakala\" butonuna bas", color = OnSurfaceDim, fontSize = 14.sp)
                }
            }
        }

        screenshot?.let {
            Spacer(Modifier.height(8.dp))
            Text("${it.width}×${it.height}px", fontSize = 11.sp, color = OnSurfaceDim)
        }
    }
}

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): String {
    return try {
        val filename = "tv_screenshot_${System.currentTimeMillis()}.png"
        val outputStream: OutputStream?

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TVRemote")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            outputStream = uri?.let { context.contentResolver.openOutputStream(it) }
        } else {
            @Suppress("DEPRECATION")
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            val file = java.io.File(dir, filename)
            outputStream = java.io.FileOutputStream(file)
        }

        outputStream?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        "Galeriye kaydedildi: $filename"
    } catch (e: Exception) {
        "Hata: ${e.message}"
    }
}
