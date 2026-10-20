package com.smdozk.tvremote.ui.screen

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smdozk.tvremote.ui.theme.CardColor
import com.smdozk.tvremote.ui.theme.Cyan
import com.smdozk.tvremote.viewmodel.MainViewModel
import java.io.OutputStream

@Composable
fun ScreenshotScreen(vm: MainViewModel) {
    val screenshot by vm.screenshot.collectAsState()
    val isCapturing by vm.isCapturing.collectAsState()
    val context = LocalContext.current
    var savedMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Ekran Görüntüsü", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Cyan)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                screenshot?.let { bmp ->
                    IconButton(onClick = {
                        savedMessage = saveBitmapToGallery(context, bmp)
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Kaydet", tint = Cyan)
                    }
                }
                Button(
                    onClick = vm::captureScreenshot,
                    enabled = !isCapturing,
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan)
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.Black)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isCapturing) "Alınıyor..." else "Yakala",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        savedMessage?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (msg.startsWith("Hata")) Color(0x33CF6679) else Color(0x3300BCD4)
                )
            ) {
                Text(
                    text = msg,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    color = if (msg.startsWith("Hata")) Color(0xFFCF6679) else Cyan
                )
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
                .background(CardColor, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            when {
                isCapturing -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Cyan, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("TV ekranı alınıyor...", color = Color.Gray)
                }
                screenshot != null -> Image(
                    bitmap = screenshot!!.asImageBitmap(),
                    contentDescription = "TV ekran görüntüsü",
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentScale = ContentScale.Fit
                )
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Tv,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Ekran görüntüsü almak için\n\"Yakala\" butonuna bas", color = Color.Gray, fontSize = 14.sp)
                }
            }
        }

        screenshot?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "Görüntü: ${it.width}×${it.height}px",
                fontSize = 11.sp,
                color = Color.Gray
            )
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
