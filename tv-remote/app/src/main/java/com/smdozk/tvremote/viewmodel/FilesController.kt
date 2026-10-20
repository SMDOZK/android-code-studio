package com.smdozk.tvremote.viewmodel

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.smdozk.tvremote.adb.AdbManager
import com.smdozk.tvremote.adb.ProgressSink
import com.smdozk.tvremote.adb.ProgressSource
import com.smdozk.tvremote.model.Notice
import com.smdozk.tvremote.model.RemoteFile
import com.smdozk.tvremote.model.Transfer
import com.smdozk.tvremote.model.TransferKind
import com.smdozk.tvremote.model.TransferStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.sink
import okio.source
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class FilesController(
    private val app: Application,
    private val adb: AdbManager,
    private val scope: CoroutineScope
) {
    private val _path = MutableStateFlow(HOME)
    val path: StateFlow<String> = _path.asStateFlow()

    private val _entries = MutableStateFlow<List<RemoteFile>>(emptyList())
    val entries: StateFlow<List<RemoteFile>> = _entries.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _transfer = MutableStateFlow<Transfer?>(null)
    val transfer: StateFlow<Transfer?> = _transfer.asStateFlow()

    private val _message = MutableStateFlow<Notice?>(null)
    val message: StateFlow<Notice?> = _message.asStateFlow()

    private var loaded = false
    private var loadJob: Job? = null

    fun ensureLoaded() {
        if (!loaded) refresh()
    }

    fun open(dir: String) {
        _path.value = dir.ifEmpty { "/" }
        refresh()
    }

    fun up() = open(parentOf(_path.value))

    fun refresh() {
        loadJob?.cancel()
        val dir = _path.value
        loadJob = scope.launch {
            _isLoading.value = true
            adb.listDirectory(dir)
                .onSuccess {
                    _entries.value = it
                    loaded = true
                }
                .onFailure {
                    _entries.value = emptyList()
                    _message.value = Notice("Klasör okunamadı: ${it.message}", isError = true)
                }
            _isLoading.value = false
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun upload(uri: Uri) {
        if (_transfer.value != null) return
        val dir = _path.value
        scope.launch {
            val meta = withContext(Dispatchers.IO) { app.contentResolver.queryMeta(uri) }
            val name = meta.name ?: "dosya"
            val target = if (dir == "/") "/$name" else "$dir/$name"
            _transfer.value = Transfer(name, TransferKind.Upload, 0, meta.size)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val input = app.contentResolver.openInputStream(uri) ?: error("Dosya açılamadı")
                    input.use {
                        val source = ProgressSource(it.source(), progressUpdater(name, TransferKind.Upload, meta.size))
                        adb.upload(source, target).getOrThrow()
                    }
                }
            }
            _transfer.value = null
            result
                .onSuccess {
                    _message.value = Notice("TV'ye gönderildi: $name")
                    refresh()
                }
                .onFailure { _message.value = Notice("Gönderilemedi: ${describeTransferError(it)}", isError = true) }
        }
    }

    fun download(file: RemoteFile) {
        if (_transfer.value != null) return
        scope.launch {
            _transfer.value = Transfer(file.name, TransferKind.Download, 0, file.size)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val target = DownloadTarget.create(app, file.name)
                    try {
                        target.output.use { out ->
                            val sink = ProgressSink(out.sink(), progressUpdater(file.name, TransferKind.Download, file.size))
                            adb.download(file.path, sink).getOrThrow()
                        }
                        target.commit()
                        target.location
                    } catch (e: Throwable) {
                        target.discard()
                        throw e
                    }
                }
            }
            _transfer.value = null
            _message.value = result.fold(
                onSuccess = { Notice("İndirildi: $it") },
                onFailure = { Notice("İndirilemedi: ${describeTransferError(it)}", isError = true) }
            )
        }
    }

    fun delete(file: RemoteFile) {
        scope.launch {
            adb.delete(file.path)
                .onSuccess {
                    _message.value = Notice("Silindi: ${file.name}")
                    refresh()
                }
                .onFailure { _message.value = Notice("Silinemedi: ${it.message}", isError = true) }
        }
    }

    fun install(file: RemoteFile) {
        if (_transfer.value != null) return
        scope.launch {
            _transfer.value = Transfer(file.name, TransferKind.Install, 0, -1, TransferStage.Finishing)
            val result = adb.installFromDevice(file.path)
            _transfer.value = null
            _message.value = result.fold(
                onSuccess = { Notice("Kuruldu: ${file.name}") },
                onFailure = { Notice("Kurulamadı: ${describeInstallError(it)}", isError = true) }
            )
        }
    }

    fun reset() {
        loadJob?.cancel()
        loaded = false
        _path.value = HOME
        _entries.value = emptyList()
        _isLoading.value = false
        _transfer.value = null
        _message.value = null
    }

    private fun progressUpdater(title: String, kind: TransferKind, total: Long): (Long) -> Unit {
        var lastStep = -1L
        return { bytes ->
            // One update per percent (or per 256 KB when the size is unknown) keeps the UI from flooding.
            val step = if (total > 0) bytes * 100 / total else bytes / (256 * 1024)
            if (step != lastStep) {
                lastStep = step
                _transfer.value = Transfer(title, kind, bytes, total)
            }
        }
    }

    private class DownloadTarget(
        val output: OutputStream,
        val location: String,
        private val onCommit: () -> Unit,
        private val onDiscard: () -> Unit
    ) {
        fun commit() = onCommit()
        fun discard() = runCatching { onDiscard() }

        companion object {
            fun create(context: Context, name: String): DownloadTarget {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/TVRemote")
                        // Hidden from other apps until the transfer completes.
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: error("İndirme dosyası oluşturulamadı")
                    val output = resolver.openOutputStream(uri) ?: error("İndirme dosyası açılamadı")
                    return DownloadTarget(
                        output = output,
                        location = "İndirilenler/TVRemote/$name",
                        onCommit = {
                            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                        },
                        onDiscard = { resolver.delete(uri, null, null) }
                    )
                }
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                val file = File(dir, name)
                return DownloadTarget(FileOutputStream(file), file.absolutePath, onCommit = {}, onDiscard = { file.delete() })
            }
        }
    }

    companion object {
        const val HOME = "/sdcard"

        fun parentOf(path: String): String =
            if (path == "/" || !path.contains('/')) "/" else path.substringBeforeLast('/').ifEmpty { "/" }
    }
}
