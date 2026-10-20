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

    private var dadb: Dadb? = null
    private var keyPair: AdbKeyPair? = null

    val isConnected: Boolean get() = dadb != null

    private fun getOrCreateKeyPair(): AdbKeyPair {
        keyPair?.let { return it }
        val keyFile = File(filesDir, "adbkey")
        return if (keyFile.exists()) {
            AdbKeyPair.read(keyFile)
        } else {
            AdbKeyPair.generate(keyFile)
        }.also { keyPair = it }
    }

    suspend fun connect(host: String, port: Int = 5555): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dadb?.close()
            dadb = null
            val kp = getOrCreateKeyPair()
            val connection = Dadb.create(host, port, kp)
            val test = connection.shell("echo __ok__")
            if (test.output.contains("__ok__")) {
                dadb = connection
                Result.success(Unit)
            } else {
                connection.close()
                Result.failure(Exception("Bağlantı testi başarısız"))
            }
        } catch (e: Exception) {
            dadb = null
            Result.failure(e)
        }
    }

    fun disconnect() {
        try { dadb?.close() } catch (_: Exception) {}
        dadb = null
    }

    suspend fun sendKey(keyCode: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            require().shell("input keyevent $keyCode")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun shell(command: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val result = require().shell(command)
            val output = if (result.errorOutput.isNotBlank() && result.exitCode != 0) {
                "HATA: ${result.errorOutput}\n${result.output}"
            } else {
                result.output
            }
            Result.success(output)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listPackages(thirdPartyOnly: Boolean = true): Result<List<AppInfo>> = withContext(Dispatchers.IO) {
        try {
            val flag = if (thirdPartyOnly) "-3" else ""
            val result = require().shell("pm list packages $flag")
            val packages = result.output.trim().split("\n")
                .filter { it.startsWith("package:") }
                .map { line ->
                    val pkg = line.removePrefix("package:").trim()
                    AppInfo(packageName = pkg, isSystem = !thirdPartyOnly)
                }
                .sortedBy { it.packageName }
            Result.success(packages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun launchApp(packageName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            require().shell("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun stopApp(packageName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            require().shell("am force-stop $packageName")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uninstallApp(packageName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            require().shell("pm uninstall $packageName")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun installApk(apkFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            require().install(apkFile)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun screenshot(cacheDir: File): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            val adb = require()
            val remotePath = "/sdcard/.tvremote_tmp.png"
            adb.shell("screencap -p $remotePath")

            val localFile = File(cacheDir, "tv_screen_${System.currentTimeMillis()}.png")
            adb.pull(remotePath, localFile)
            adb.shell("rm -f $remotePath")

            val bitmap = BitmapFactory.decodeFile(localFile.absolutePath)
            localFile.delete()

            bitmap?.let { Result.success(it) }
                ?: Result.failure(Exception("Görüntü çözümlenemedi"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDeviceInfo(): Result<DeviceInfo> = withContext(Dispatchers.IO) {
        try {
            val adb = require()
            val model = adb.shell("getprop ro.product.model").output.trim()
            val manufacturer = adb.shell("getprop ro.product.manufacturer").output.trim()
            val androidVersion = adb.shell("getprop ro.build.version.release").output.trim()
            val apiLevel = adb.shell("getprop ro.build.version.sdk").output.trim().toIntOrNull() ?: 0
            Result.success(DeviceInfo(model, manufacturer, androidVersion, apiLevel))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushFile(localFile: File, remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            require().push(localFile, remotePath)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun inputText(text: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val escaped = text.replace(" ", "%s").replace("'", "\\'")
            require().shell("input text '$escaped'")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun require(): Dadb = dadb ?: throw IllegalStateException("TV'ye bağlı değil")
}
