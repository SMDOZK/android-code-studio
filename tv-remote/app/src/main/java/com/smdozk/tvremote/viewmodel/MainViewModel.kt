package com.smdozk.tvremote.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smdozk.tvremote.adb.AdbManager
import com.smdozk.tvremote.adb.ProgressSource
import com.smdozk.tvremote.adb.ScreenMirror
import com.smdozk.tvremote.adb.TvScanner
import com.smdozk.tvremote.model.AppInfo
import com.smdozk.tvremote.model.ConnectionState
import com.smdozk.tvremote.model.DeviceInfo
import com.smdozk.tvremote.model.DiscoveredTv
import com.smdozk.tvremote.model.Notice
import com.smdozk.tvremote.model.TerminalEntry
import com.smdozk.tvremote.model.Transfer
import com.smdozk.tvremote.model.TransferKind
import com.smdozk.tvremote.model.TransferStage
import com.smdozk.tvremote.model.TvInput
import com.smdozk.tvremote.model.TvKind
import com.smdozk.tvremote.webos.WebOsClient
import com.smdozk.tvremote.webos.WebOsException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Source
import okio.source
import java.io.File
import java.net.SocketTimeoutException
import kotlin.math.roundToInt

enum class ScanState { Idle, Scanning, Done, NoWifi }

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val adbManager = AdbManager(application.filesDir)
    private val prefs = application.getSharedPreferences("tv_remote", Context.MODE_PRIVATE)

    val files = FilesController(application, adbManager, viewModelScope)
    val logcat = LogcatController(adbManager, viewModelScope)
    val mirror = ScreenMirror(adbManager, viewModelScope)

    val savedHost: String get() = prefs.getString(KEY_HOST, null) ?: "192.168.1."
    val savedPort: Int get() = prefs.getInt(KEY_PORT, DEFAULT_PORT)
    val shouldAutoScan: Boolean get() = !prefs.contains(KEY_HOST) && _scanState.value == ScanState.Idle

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
    val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo.asStateFlow()

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _install = MutableStateFlow<Transfer?>(null)
    val install: StateFlow<Transfer?> = _install.asStateFlow()

    private val _installMessage = MutableStateFlow<Notice?>(null)
    val installMessage: StateFlow<Notice?> = _installMessage.asStateFlow()

    private val _screenshot = MutableStateFlow<Bitmap?>(null)
    val screenshot: StateFlow<Bitmap?> = _screenshot.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _screenshotNotice = MutableStateFlow<Notice?>(null)
    val screenshotNotice: StateFlow<Notice?> = _screenshotNotice.asStateFlow()

    private val _terminalHistory = MutableStateFlow<List<TerminalEntry>>(emptyList())
    val terminalHistory: StateFlow<List<TerminalEntry>> = _terminalHistory.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _appsFilter = MutableStateFlow(true)
    val appsFilter: StateFlow<Boolean> = _appsFilter.asStateFlow()

    private val _discovered = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    val discovered: StateFlow<List<DiscoveredTv>> = _discovered.asStateFlow()

    private val _scanState = MutableStateFlow(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _backend = MutableStateFlow<TvKind?>(null)
    val backend: StateFlow<TvKind?> = _backend.asStateFlow()

    private val _pairingPrompt = MutableStateFlow(false)
    val pairingPrompt: StateFlow<Boolean> = _pairingPrompt.asStateFlow()

    private val _inputs = MutableStateFlow<List<TvInput>?>(null)
    val inputs: StateFlow<List<TvInput>?> = _inputs.asStateFlow()

    @Volatile
    private var tvSize: Pair<Int, Int>? = null

    @Volatile
    private var webOs: WebOsClient? = null

    private sealed interface RemoteCommand {
        data class Key(val code: Int) : RemoteCommand
        data class Text(val text: String) : RemoteCommand
        data class Shell(val command: String) : RemoteCommand
    }

    // A single consumer keeps keys, text and taps in order; parallel shells would reorder them.
    private val commands = Channel<RemoteCommand>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        viewModelScope.launch {
            var carried: RemoteCommand? = null
            while (true) {
                val command = carried ?: commands.receive()
                carried = null
                val tv = webOs
                val result = when {
                    tv != null -> when (command) {
                        is RemoteCommand.Key -> runCatching { tv.sendKey(command.code) }
                        is RemoteCommand.Text -> runCatching { tv.insertText(command.text) }
                        is RemoteCommand.Shell -> Result.success(Unit)
                    }
                    command is RemoteCommand.Text -> adbManager.inputText(command.text)
                    command is RemoteCommand.Shell -> adbManager.shell(command.command).map { }
                    command is RemoteCommand.Key -> {
                        val batch = mutableListOf(command.code)
                        while (batch.size < MAX_KEY_BATCH) {
                            val next = commands.tryReceive().getOrNull() ?: break
                            if (next is RemoteCommand.Key) batch += next.code else { carried = next; break }
                        }
                        adbManager.sendKeys(batch)
                    }
                    else -> Result.success(Unit)
                }
                // A webOS error reply (e.g. an unsupported button) leaves the connection intact.
                result.onFailure { if (it !is WebOsException) onConnectionLost(it) }
            }
        }
    }

    fun connect(ip: String, port: Int = DEFAULT_PORT) {
        val host = ip.trim()
        viewModelScope.launch {
            _connectionState.value = ConnectionState.CONNECTING
            _connectionError.value = null
            val result = when (detectBackend(host, port)) {
                TvKind.WebOs -> connectWebOs(host)
                TvKind.Adb -> adbManager.connect(host, port).map { TvKind.Adb }
                null -> Result.failure(SocketTimeoutException())
            }
            result
                .onSuccess { kind ->
                    prefs.edit().putString(KEY_HOST, host).putInt(KEY_PORT, port).apply()
                    _backend.value = kind
                    _connectionState.value = ConnectionState.CONNECTED
                    if (kind == TvKind.WebOs) loadWebOsInfo() else loadAdbInfo()
                }
                .onFailure { e ->
                    _connectionState.value = ConnectionState.ERROR
                    _connectionError.value = describeConnectError(e)
                }
        }
    }

    // Same IP field for both kinds of TV: whichever service answers decides the protocol.
    private suspend fun detectBackend(host: String, adbPort: Int): TvKind? = withContext(Dispatchers.IO) {
        coroutineScope {
            val plain = async { TvScanner.isOpen(host, TvScanner.WEBOS_PORT, DETECT_TIMEOUT_MS) }
            val secure = async { TvScanner.isOpen(host, TvScanner.WEBOS_SECURE_PORT, DETECT_TIMEOUT_MS) }
            val adb = async { TvScanner.isOpen(host, adbPort, DETECT_TIMEOUT_MS) }
            when {
                plain.await() || secure.await() -> TvKind.WebOs
                adb.await() -> TvKind.Adb
                else -> null
            }
        }
    }

    private suspend fun connectWebOs(host: String): Result<TvKind> {
        val client = WebOsClient(host) { error -> viewModelScope.launch { onConnectionLost(error) } }
        return try {
            val key = client.connect(prefs.getString(webOsKeyFor(host), null)) { _pairingPrompt.value = true }
            prefs.edit().putString(webOsKeyFor(host), key).apply()
            webOs = client
            Result.success(TvKind.WebOs)
        } catch (e: Throwable) {
            client.close()
            Result.failure(e)
        } finally {
            _pairingPrompt.value = false
        }
    }

    private fun webOsKeyFor(host: String) = "webos_key_$host"

    private suspend fun loadAdbInfo() {
        adbManager.getDeviceInfo().onSuccess { _deviceInfo.value = it }
        adbManager.displaySize().onSuccess { tvSize = it }
    }

    private suspend fun loadWebOsInfo() {
        val tv = webOs ?: return
        val software = runCatching { tv.softwareInfo() }.getOrNull()
        val model = tv.systemInfo?.optString("modelName").orEmpty()
            .ifBlank { software?.optString("model_name").orEmpty() }
            .ifBlank { "webOS TV" }
        // product_name is e.g. "webOSTV 8.0"; major_ver is the firmware version, not the webOS one.
        val platform = software?.optString("product_name").orEmpty()
            .replace("webOSTV", "webOS").trim()
            .ifBlank { "webOS" }
        _deviceInfo.value = DeviceInfo(
            model = model,
            manufacturer = "",
            androidVersion = "",
            apiLevel = 0,
            platform = platform
        )
    }

    fun disconnect() {
        // Stop the streams first so they end quietly instead of reporting a dropped connection.
        mirror.stop()
        logcat.stop()
        webOs?.close()
        webOs = null
        adbManager.disconnect()
        while (commands.tryReceive().isSuccess) Unit
        _backend.value = null
        _inputs.value = null
        tvSize = null
        files.reset()
        _connectionState.value = ConnectionState.DISCONNECTED
        _deviceInfo.value = null
        _apps.value = emptyList()
        _screenshot.value = null
        _terminalHistory.value = emptyList()
        _connectionError.value = null
        _install.value = null
        _installMessage.value = null
    }

    private fun onConnectionLost(error: Throwable) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        disconnect()
        _connectionState.value = ConnectionState.ERROR
        _connectionError.value = "Bağlantı koptu: ${error.message ?: error.javaClass.simpleName}"
    }

    fun scanNetwork() {
        if (_scanState.value == ScanState.Scanning) return
        val subnet = TvScanner.wifiSubnet(getApplication())
        if (subnet == null) {
            _scanState.value = ScanState.NoWifi
            return
        }
        _discovered.value = emptyList()
        _scanState.value = ScanState.Scanning
        viewModelScope.launch {
            TvScanner.probe(TvScanner.candidateHosts(subnet)) { host, kind ->
                var added = false
                _discovered.update { found ->
                    val existing = found.firstOrNull { it.host == host }
                    when {
                        existing == null -> {
                            added = true
                            (found + DiscoveredTv(host, null, kind)).sortedBy { it.host.substringAfterLast('.').toIntOrNull() ?: 0 }
                        }
                        // A webOS port is the more specific signal than a stray ADB port.
                        kind == TvKind.WebOs && existing.kind != TvKind.WebOs ->
                            found.map { if (it.host == host) it.copy(kind = TvKind.WebOs) else it }
                        else -> found
                    }
                }
                if (!added) return@probe
                viewModelScope.launch(Dispatchers.IO) {
                    TvScanner.hostName(host)?.let { name ->
                        _discovered.update { found -> found.map { if (it.host == host) it.copy(name = name) else it } }
                    }
                }
            }
            _scanState.value = ScanState.Done
        }
    }

    fun sendKey(keyCode: Int) {
        commands.trySend(RemoteCommand.Key(keyCode))
    }

    fun inputText(text: String) {
        if (text.isNotEmpty()) commands.trySend(RemoteCommand.Text(text))
    }

    fun tapScreen(fx: Float, fy: Float) {
        val (width, height) = tvSize ?: return
        commands.trySend(RemoteCommand.Shell("input tap ${scale(fx, width)} ${scale(fy, height)}"))
    }

    fun swipeScreen(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long) {
        val (width, height) = tvSize ?: return
        commands.trySend(
            RemoteCommand.Shell(
                "input swipe ${scale(fromX, width)} ${scale(fromY, height)} ${scale(toX, width)} ${scale(toY, height)} $durationMs"
            )
        )
    }

    private fun scale(fraction: Float, size: Int): Int = (fraction.coerceIn(0f, 1f) * (size - 1)).roundToInt()

    // Long side 1280 keeps the stream light on Wi-Fi; encoders want dimensions in multiples of 16.
    fun startMirror(surface: Surface) {
        val (tvWidth, tvHeight) = tvSize ?: (1920 to 1080)
        val (width, height) = if (tvWidth >= tvHeight) {
            MIRROR_LONG_SIDE to roundTo16(MIRROR_LONG_SIDE * tvHeight / tvWidth)
        } else {
            roundTo16(MIRROR_LONG_SIDE * tvWidth / tvHeight) to MIRROR_LONG_SIDE
        }
        mirror.start(surface, width, height)
    }

    private fun roundTo16(value: Int): Int = ((value + 8) / 16 * 16).coerceAtLeast(16)

    fun loadApps(thirdPartyOnly: Boolean = _appsFilter.value) {
        viewModelScope.launch {
            _appsFilter.value = thirdPartyOnly
            _isLoadingApps.value = true
            val tv = webOs
            val result = if (tv != null) {
                runCatching {
                    tv.launchPoints()
                        .filter { !thirdPartyOnly || it.removable }
                        .map { AppInfo(it.id, isSystem = !it.removable, title = it.title) }
                        .sortedBy { it.displayName.lowercase() }
                }
            } else {
                adbManager.listPackages(thirdPartyOnly)
            }
            result.onSuccess { _apps.value = it }
            _isLoadingApps.value = false
        }
    }

    fun launchApp(packageName: String) {
        viewModelScope.launch {
            val tv = webOs
            if (tv != null) runCatching { tv.launch(packageName) } else adbManager.launchApp(packageName)
        }
    }

    fun stopApp(packageName: String) {
        viewModelScope.launch {
            val tv = webOs
            if (tv != null) runCatching { tv.closeApp(packageName) } else adbManager.stopApp(packageName)
        }
    }

    fun showInputs() {
        val tv = webOs ?: return
        viewModelScope.launch {
            runCatching { tv.inputs() }
                .onSuccess { _inputs.value = it }
                // Firmware without the input list API still has the LG input hub button.
                .onFailure { sendKey(com.smdozk.tvremote.adb.TvKeyCode.TV_INPUT) }
        }
    }

    fun switchInput(input: TvInput) {
        _inputs.value = null
        val tv = webOs ?: return
        viewModelScope.launch { runCatching { tv.switchInput(input.id) } }
    }

    fun dismissInputs() {
        _inputs.value = null
    }

    fun uninstallApp(packageName: String) {
        viewModelScope.launch {
            adbManager.uninstallApp(packageName).onSuccess { loadApps() }
        }
    }

    fun installApk(uri: Uri) {
        if (_install.value != null) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            _installMessage.value = null
            val meta = withContext(Dispatchers.IO) { app.contentResolver.queryMeta(uri) }
            val name = meta.name ?: "uygulama.apk"
            _install.value = Transfer(name, TransferKind.Install, 0, meta.size)
            val result = withContext(Dispatchers.IO) {
                var staged: File? = null
                try {
                    runCatching {
                        // `cmd package install -S` needs the size up front; stage the file when the provider hides it.
                        val (input, size) = if (meta.size > 0) {
                            (app.contentResolver.openInputStream(uri) ?: error("Dosya açılamadı")).source() to meta.size
                        } else {
                            val copy = File(app.cacheDir, "install.apk").also { staged = it }
                            (app.contentResolver.openInputStream(uri) ?: error("Dosya açılamadı")).use { inStream ->
                                copy.outputStream().use { inStream.copyTo(it) }
                            }
                            copy.source() to copy.length()
                        }
                        input.use { adbManager.install(trackInstall(input, name, size), size).getOrThrow() }
                    }
                } finally {
                    staged?.delete()
                }
            }
            _install.value = null
            result
                .onSuccess {
                    _installMessage.value = Notice("Kuruldu: $name")
                    loadApps()
                }
                .onFailure { _installMessage.value = Notice("Kurulamadı: ${describeInstallError(it)}", isError = true) }
        }
    }

    private fun trackInstall(source: Source, name: String, size: Long): Source {
        var lastStep = -1L
        return ProgressSource(source) { bytes ->
            val step = bytes * 100 / size
            if (step != lastStep) {
                lastStep = step
                // After the last byte the TV still has to verify and install the package.
                val stage = if (bytes >= size) TransferStage.Finishing else TransferStage.Running
                _install.value = Transfer(name, TransferKind.Install, bytes, size, stage)
            }
        }
    }

    fun clearInstallMessage() {
        _installMessage.value = null
    }

    fun captureScreenshot() {
        if (_isCapturing.value) return
        viewModelScope.launch {
            _isCapturing.value = true
            _screenshotNotice.value = null
            adbManager.screenshot()
                .onSuccess { bitmap ->
                    _screenshot.value = bitmap
                    _screenshotNotice.value = withContext(Dispatchers.IO) { saveToGallery(getApplication(), bitmap) }
                }
                .onFailure { e ->
                    _screenshotNotice.value = Notice("Ekran görüntüsü alınamadı: ${e.message ?: e.javaClass.simpleName}", isError = true)
                }
            _isCapturing.value = false
        }
    }

    fun clearScreenshotNotice() {
        _screenshotNotice.value = null
    }

    fun executeCommand(command: String) {
        viewModelScope.launch {
            _isExecuting.value = true
            val result = adbManager.shell(command)
            addTerminalEntry(
                command = command,
                output = result.getOrElse { "Hata: ${it.message}" },
                isError = result.isFailure
            )
            _isExecuting.value = false
        }
    }

    fun clearTerminal() {
        _terminalHistory.value = emptyList()
    }

    private fun addTerminalEntry(command: String, output: String, isError: Boolean = false) {
        _terminalHistory.update { it + TerminalEntry(command, output.trim(), isError) }
    }

    override fun onCleared() {
        super.onCleared()
        mirror.stop()
        logcat.stop()
        webOs?.close()
        adbManager.disconnect()
    }

    private companion object {
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val DEFAULT_PORT = 5555
        const val MAX_KEY_BATCH = 16
        const val MIRROR_LONG_SIDE = 1280
        const val DETECT_TIMEOUT_MS = 1500
    }
}
