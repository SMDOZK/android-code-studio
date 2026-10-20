package com.smdozk.tvremote.adb

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.smdozk.tvremote.model.AppInfo
import com.smdozk.tvremote.model.DeviceInfo
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AdbManager(private val filesDir: File) {

    @Volatile
    private var dadb: Dadb? = null
    private var keyPair: AdbKeyPair? = null

    private fun getOrCreateKeyPair(): AdbKeyPair {
        keyPair?.let { return it }
        val privateKey = File(filesDir, "adbkey")
        val publicKey = File(filesDir, "adbkey.pub")
        // The TV can only show its "allow debugging" prompt if we can send the public key.
        if (!privateKey.exists() || !publicKey.exists()) {
            AdbKeyPair.generate(privateKey, publicKey)
        }
        return AdbKeyPair.read(privateKey, publicKey).also { keyPair = it }
    }

    suspend fun connect(host: String, port: Int = 5555): Result<Unit> = withContext(Dispatchers.IO) {
        disconnect()
        runCatching {
            // No socket timeout: the handshake blocks until the user accepts the prompt on the TV.
            val connection = Dadb.create(host, port, getOrCreateKeyPair(), connectTimeout = CONNECT_TIMEOUT_MS)
            try {
                check(connection.shell("echo __ok__").output.contains("__ok__")) { "Bağlantı testi başarısız" }
            } catch (e: Throwable) {
                connection.close()
                throw e
            }
            dadb = connection
        }
    }

    fun disconnect() {
        val current = dadb
        dadb = null
        runCatching { current?.close() }
    }

    suspend fun sendKeys(keyCodes: List<Int>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { require().shell("input keyevent ${keyCodes.joinToString(" ")}") }.map { }
    }

    suspend fun shell(command: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val result = require().shell(command)
            if (result.errorOutput.isNotBlank() && result.exitCode != 0) {
                "HATA: ${result.errorOutput}\n${result.output}"
            } else {
                result.output
            }
        }
    }

    suspend fun listPackages(thirdPartyOnly: Boolean = true): Result<List<AppInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val flag = if (thirdPartyOnly) "-3" else ""
            require().shell("pm list packages $flag").output.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("package:") }
                .map { AppInfo(packageName = it.removePrefix("package:"), isSystem = !thirdPartyOnly) }
                .sortedBy { it.packageName }
                .toList()
        }
    }

    suspend fun launchApp(packageName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { require().shell("monkey -p $packageName -c android.intent.category.LAUNCHER 1") }.map { }
    }

    suspend fun stopApp(packageName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { require().shell("am force-stop $packageName") }.map { }
    }

    suspend fun uninstallApp(packageName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { require().shell("pm uninstall $packageName") }.map { }
    }

    suspend fun installApk(apkFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { require().install(apkFile) }
    }

    suspend fun screenshot(): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            // exec: streams raw stdout, so the PNG arrives without a temp file on the TV.
            val png = require().open("exec:screencap -p").use { it.source.readByteArray() }
            val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size) ?: error("Görüntü çözümlenemedi")
            // Uploads the texture now, off the UI thread, instead of on the first frame that draws it.
            bitmap.prepareToDraw()
            bitmap
        }
    }

    suspend fun getDeviceInfo(): Result<DeviceInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val props = require().shell(
                "getprop ro.product.manufacturer; getprop ro.product.model; " +
                    "getprop ro.build.version.release; getprop ro.build.version.sdk"
            ).output.lines().map { it.trim() }
            DeviceInfo(
                manufacturer = props.getOrElse(0) { "" },
                model = props.getOrElse(1) { "" },
                androidVersion = props.getOrElse(2) { "" },
                apiLevel = props.getOrNull(3)?.toIntOrNull() ?: 0
            )
        }
    }

    suspend fun pushFile(localFile: File, remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { require().push(localFile, remotePath) }
    }

    suspend fun inputText(text: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Close-escape-reopen keeps quotes inside the single-quoted shell argument; `input` reads %s as space.
            val escaped = text.replace("'", "'\\''").replace(" ", "%s")
            require().shell("input text '$escaped'")
        }.map { }
    }

    private fun require(): Dadb = dadb ?: throw IllegalStateException("TV'ye bağlı değil")

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
    }
}
