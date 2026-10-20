package com.smdozk.tvremote.viewmodel

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal fun describeConnectError(error: Throwable): String = when (error) {
    is ConnectException -> "TV bağlantıyı reddetti. TV'de ADB / USB hata ayıklama açık mı?"
    is SocketTimeoutException, is NoRouteToHostException -> "TV'ye ulaşılamadı. IP adresini ve aynı Wi-Fi ağında olduğunuzu kontrol edin."
    is UnknownHostException -> "Geçersiz IP adresi."
    else -> error.message ?: error.javaClass.simpleName
}

private val INSTALL_ERRORS = mapOf(
    "INSTALL_FAILED_VERSION_DOWNGRADE" to "TV'deki sürüm daha yeni; sürüm düşürmeye izin verilmiyor.",
    "INSTALL_FAILED_UPDATE_INCOMPATIBLE" to "Uygulamanın farklı imzalı bir sürümü yüklü. Önce onu kaldırın.",
    "INSTALL_FAILED_INSUFFICIENT_STORAGE" to "TV'de yeterli depolama alanı yok.",
    "INSTALL_FAILED_OLDER_SDK" to "Bu APK, TV'nin Android sürümünü desteklemiyor.",
    "INSTALL_FAILED_NO_MATCHING_ABIS" to "APK, TV'nin işlemci mimarisine uygun değil.",
    "INSTALL_FAILED_INVALID_APK" to "APK dosyası geçersiz veya bozuk.",
    "INSTALL_PARSE_FAILED" to "APK dosyası okunamadı (bozuk veya eksik indirilmiş olabilir).",
    "INSTALL_FAILED_ALREADY_EXISTS" to "Uygulama zaten yüklü.",
    "INSTALL_FAILED_USER_RESTRICTED" to "TV, ADB üzerinden kuruluma izin vermiyor.",
)

internal fun describeInstallError(error: Throwable): String {
    val text = error.message.orEmpty()
    return INSTALL_ERRORS.entries.firstOrNull { text.contains(it.key) }?.value
        ?: text.removePrefix("Install failed: ").ifBlank { error.javaClass.simpleName }
}

internal fun describeTransferError(error: Throwable): String {
    val text = error.message.orEmpty()
    return when {
        // adbd answers FAIL instead of OKAY when it cannot write the target path.
        text.contains("FAIL") -> "TV bu konuma yazmaya izin vermedi."
        text.contains("No such file") -> "Dosya TV'de bulunamadı."
        else -> text.ifBlank { error.javaClass.simpleName }
    }
}

internal data class UriMeta(val name: String?, val size: Long)

internal fun ContentResolver.queryMeta(uri: Uri): UriMeta =
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(cursor::getString)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L
        UriMeta(name, size)
    } ?: UriMeta(null, -1L)
