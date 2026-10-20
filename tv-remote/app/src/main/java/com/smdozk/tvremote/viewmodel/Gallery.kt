package com.smdozk.tvremote.viewmodel

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.smdozk.tvremote.model.Notice
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

// Blocking: PNG-encoding a 4K frame takes about a second, so callers run this off the main thread.
internal fun saveToGallery(context: Context, bitmap: Bitmap): Notice = try {
    val filename = "tv_screenshot_${System.currentTimeMillis()}.png"
    val output: OutputStream? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/TVRemote")
        }
        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?.let { context.contentResolver.openOutputStream(it) }
    } else {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        FileOutputStream(File(dir, filename))
    }
    if (output == null) {
        Notice("Galeri dosyası oluşturulamadı", isError = true)
    } else {
        output.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Notice("Galeriye kaydedildi: Resimler/TVRemote/$filename")
    }
} catch (e: Exception) {
    Notice("Kaydedilemedi: ${e.message}", isError = true)
}
