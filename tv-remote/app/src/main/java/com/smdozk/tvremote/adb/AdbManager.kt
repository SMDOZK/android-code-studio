package com.smdozk.tvremote.adb

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.smdozk.tvremote.model.AppInfo
import com.smdozk.tvremote.model.DeviceInfo
import com.smdozk.tvremote.model.RemoteFile
import dadb.AdbKeyPair
import dadb.AdbStream
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Sink
import okio.Source
import java.io.File
import java.io.IOException

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

    suspend fun install(source: Source, size: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val adb = require()
            if (adb.supportsFeature("cmd")) {
                adb.install(source, size, "-r")
            } else {
                // dadb's own fallback pushes a File, whose mode lookup ("unix:mode") does not exist on Android.
                val staged = "/data/local/tmp/tvremote_install.apk"
                adb.push(source, staged, FILE_MODE, System.currentTimeMillis())
                val output = adb.shell("pm install -r $staged; rm -f $staged").output
                if (!output.contains("Success")) throw IOException(output.trim())
            }
        }
    }

    suspend fun installFromDevice(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val result = require().shell("pm install -r ${shellQuote(remotePath)}")
            val output = result.output + result.errorOutput
            if (!output.contains("Success")) throw IOException(output.trim())
        }
    }

    suspend fun listDirectory(path: String): Result<List<RemoteFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = path.trimEnd('/')
            val base = if (dir.isEmpty()) "" else shellQuote(dir)
            require().shell("stat -L -c '%F|%s|%Y|%n' $base/* $base/.[!.]* $base/..?* 2>/dev/null").output
                .lineSequence()
                .mapNotNull(::parseStatLine)
                .sortedWith(compareBy<RemoteFile>({ !it.isDirectory }, { it.name.lowercase() }))
                .toList()
        }
    }

    suspend fun upload(source: Source, remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { require().push(source, remotePath, FILE_MODE, System.currentTimeMillis()) }
    }

    suspend fun download(remotePath: String, sink: Sink): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { require().pull(sink, remotePath) }
    }

    suspend fun delete(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val result = require().shell("rm -rf ${shellQuote(path)}")
            if (result.exitCode != 0) throw IOException(result.errorOutput.trim().ifEmpty { "rm başarısız" })
        }
    }

    suspend fun displaySize(): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            // "Override size" follows "Physical size" when set, and is what `input tap` coordinates use.
            val match = Regex("(\\d+)x(\\d+)").findAll(require().shell("wm size").output).lastOrNull()
                ?: error("Ekran boyutu okunamadı")
            match.groupValues[1].toInt() to match.groupValues[2].toInt()
        }
    }

    fun openScreenRecord(width: Int, height: Int, bitRate: Int): AdbStream =
        require().open("exec:screenrecord --output-format=h264 --size ${width}x$height --bit-rate $bitRate -")

    fun openLogcat(): AdbStream = require().open("exec:logcat -v brief -T $LOGCAT_BACKLOG")

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

    suspend fun inputText(text: String): Result<Unit> = withContext(Dispatchers.IO) {
        // `input text` reads %s as a space.
        runCatching { require().shell("input text ${shellQuote(text.replace(" ", "%s"))}") }.map { }
    }

    private fun require(): Dadb = dadb ?: throw IllegalStateException("TV'ye bağlı değil")

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val FILE_MODE = 0x81A4 // S_IFREG | 0644
        const val LOGCAT_BACKLOG = 400
    }
}

internal fun parseStatLine(line: String): RemoteFile? {
    val parts = line.trimEnd('\r').split('|', limit = 4)
    if (parts.size < 4) return null
    val path = parts[3]
    val name = path.substringAfterLast('/')
    if (name.isEmpty() || name == "." || name == "..") return null
    return RemoteFile(
        name = name,
        path = path,
        isDirectory = parts[0].startsWith("directory"),
        size = parts[1].toLongOrNull() ?: 0L,
        modifiedSeconds = parts[2].toLongOrNull() ?: 0L
    )
}
