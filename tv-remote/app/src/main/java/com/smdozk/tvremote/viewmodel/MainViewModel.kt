package com.smdozk.tvremote.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smdozk.tvremote.adb.AdbManager
import com.smdozk.tvremote.adb.ProgressSource
import com.smdozk.tvremote.adb.ScreenMirror
import com.smdozk.tvremote.adb.TvKeyCode
import com.smdozk.tvremote.adb.TvScanner
import com.smdozk.tvremote.adb.shellQuote
import com.smdozk.tvremote.model.AppInfo
import com.smdozk.tvremote.model.ConnectionState
import com.smdozk.tvremote.model.DeviceInfo
import com.smdozk.tvremote.model.DiscoveredTv
import com.smdozk.tvremote.model.Notice
import com.smdozk.tvremote.model.SavedTv
import com.smdozk.tvremote.model.TerminalEntry
import com.smdozk.tvremote.model.Transfer
import com.smdozk.tvremote.model.TransferKind
import com.smdozk.tvremote.model.TransferStage
import com.smdozk.tvremote.model.TvInput
import com.smdozk.tvremote.model.TvKind
import com.smdozk.tvremote.net.Ssdp
import com.smdozk.tvremote.net.WakeOnLan
import com.smdozk.tvremote.ui.theme.ThemeState
import com.smdozk.tvremote.webos.WebOsClient
import com.smdozk.tvremote.webos.WebOsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
import java.net.InetAddress
import java.net.SocketTimeoutException
import javax.net.SocketFactory
import kotlin.math.roundToInt

enum class ScanState { Idle, Scanning, Done, NoWifi }

// Developer Mode on a webOS TV runs an SSH server on port 9922, so an open port means it is on.
enum class DevState { Unknown, On, Off }

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val adbManager = AdbManager(application.filesDir)
    private val prefs = application.getSharedPreferences("tv_remote", Context.MODE_PRIVATE)

    val files = FilesController(application, adbManager, viewModelScope)
    val logcat = LogcatController(adbManager, viewModelScope)
    val mirror = ScreenMirror(adbManager, viewModelScope)

    val savedHost: String get() = prefs.getString(KEY_HOST, null) ?: "192.168.1."
    val savedPort: Int get() = prefs.getInt(KEY_PORT, DEFAULT_PORT)

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

    private val _savedTv = MutableStateFlow(loadSavedTv())
    val savedTv: StateFlow<SavedTv?> = _savedTv.asStateFlow()

    // Short status line for the connect screen: auto-connect, reconnect, power-on progress.
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _volume = MutableStateFlow<Int?>(null)
    val volume: StateFlow<Int?> = _volume.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val _tvDev = MutableStateFlow(DevState.Unknown)
    val tvDev: StateFlow<DevState> = _tvDev.asStateFlow()

    private val _phoneDev = MutableStateFlow(phoneDevEnabled())
    val phoneDev: StateFlow<Boolean> = _phoneDev.asStateFlow()

    private val _pointerSpeed = MutableStateFlow(prefs.getFloat(KEY_POINTER_SPEED, 1f).coerceIn(POINTER_SPEED_MIN, POINTER_SPEED_MAX))
    val pointerSpeed: StateFlow<Float> = _pointerSpeed.asStateFlow()

    private val _gyroMode = MutableStateFlow(prefs.getBoolean(KEY_GYRO_MODE, false))
    val gyroMode: StateFlow<Boolean> = _gyroMode.asStateFlow()

    private val _gyroSpeed = MutableStateFlow(prefs.getFloat(KEY_GYRO_SPEED, 1f).coerceIn(POINTER_SPEED_MIN, POINTER_SPEED_MAX))
    val gyroSpeed: StateFlow<Float> = _gyroSpeed.asStateFlow()

    // Measured zero point of this phone's gyroscope (rad/s), from the last calibration.
    val gyroBias: FloatArray?
        get() = prefs.getString(KEY_GYRO_BIAS, null)?.split(',')?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == 3 }?.toFloatArray()

    private val _devAuto = MutableStateFlow(prefs.getBoolean(KEY_DEV_AUTO, true))
    val devAuto: StateFlow<Boolean> = _devAuto.asStateFlow()

    private val _pendingLink = MutableStateFlow<String?>(null)
    val pendingLink: StateFlow<String?> = _pendingLink.asStateFlow()

    private val _remoteNotice = MutableStateFlow<Notice?>(null)
    val remoteNotice: StateFlow<Notice?> = _remoteNotice.asStateFlow()

    @Volatile
    private var tvSize: Pair<Int, Int>? = null

    @Volatile
    private var webOs: WebOsClient? = null

    private var connectJob: Job? = null
    private var devWatchJob: Job? = null
    private var scanJob: Job? = null
    private var currentHost: String? = null
    private var currentPort: Int = DEFAULT_PORT

    // Auto-connect runs until the first success or until the user disconnects on purpose.
    private var autoConnectArmed = true
    private var directAttempted = false
    private var lastPowerOffAt = Long.MIN_VALUE / 2

    private sealed interface RemoteCommand {
        data class Key(val code: Int) : RemoteCommand
        data class Text(val text: String, val submit: Boolean) : RemoteCommand
        data class Shell(val command: String) : RemoteCommand
        data class Button(val name: String) : RemoteCommand
    }

    private sealed interface PointerEvent {
        data class Move(val dx: Float, val dy: Float) : PointerEvent
        data class Scroll(val dx: Float, val dy: Float) : PointerEvent
        data object Click : PointerEvent
    }

    // A single consumer keeps keys, text and taps in order; parallel shells would reorder them.
    private val commands = Channel<RemoteCommand>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val pointerEvents = Channel<PointerEvent>(Channel.UNLIMITED)

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
                        is RemoteCommand.Text -> runCatching {
                            if (command.text.isNotEmpty()) tv.insertText(command.text)
                            if (command.submit) tv.sendEnter()
                        }
                        is RemoteCommand.Button -> runCatching { tv.button(command.name) }
                        is RemoteCommand.Shell -> Result.success(Unit)
                    }
                    command is RemoteCommand.Text -> adbManager.inputText(command.text).mapCatching {
                        if (command.submit) adbManager.sendKeys(listOf(KEYCODE_ENTER)).getOrThrow()
                    }
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
        viewModelScope.launch { pumpPointer() }
    }

    // Touchpad moves arrive faster than the TV needs them: merge whatever queued up into one message.
    private suspend fun pumpPointer() {
        var carried: PointerEvent? = null
        var restX = 0f
        var restY = 0f
        while (true) {
            val event = carried ?: pointerEvents.receive()
            carried = null
            val tv = webOs
            if (tv == null) {
                restX = 0f
                restY = 0f
                continue
            }
            when (event) {
                is PointerEvent.Click -> runCatching { tv.click() }
                is PointerEvent.Move, is PointerEvent.Scroll -> {
                    val scroll = event is PointerEvent.Scroll
                    var x = if (event is PointerEvent.Move) event.dx else (event as PointerEvent.Scroll).dx
                    var y = if (event is PointerEvent.Move) event.dy else (event as PointerEvent.Scroll).dy
                    while (true) {
                        val next = pointerEvents.tryReceive().getOrNull() ?: break
                        when {
                            !scroll && next is PointerEvent.Move -> { x += next.dx; y += next.dy }
                            scroll && next is PointerEvent.Scroll -> { x += next.dx; y += next.dy }
                            else -> { carried = next; break }
                        }
                    }
                    if (scroll) {
                        val sx = x.roundToInt()
                        val sy = y.roundToInt()
                        if (sx != 0 || sy != 0) runCatching { tv.scroll(sx, sy) }
                    } else {
                        // Keep sub-pixel leftovers so slow drags still move the cursor.
                        x += restX
                        y += restY
                        val mx = x.toInt()
                        val my = y.toInt()
                        restX = x - mx
                        restY = y - my
                        if (mx != 0 || my != 0) runCatching { tv.move(mx, my) }
                    }
                    delay(POINTER_INTERVAL_MS)
                }
            }
        }
    }

    fun setPointerSpeed(speed: Float) {
        val value = speed.coerceIn(POINTER_SPEED_MIN, POINTER_SPEED_MAX)
        _pointerSpeed.value = value
        prefs.edit().putFloat(KEY_POINTER_SPEED, value).apply()
    }

    val lightTheme: Boolean get() = prefs.getBoolean(KEY_LIGHT_THEME, true)

    fun setLightTheme(on: Boolean) {
        ThemeState.light = on
        prefs.edit().putBoolean(KEY_LIGHT_THEME, on).apply()
    }

    fun setGyroMode(on: Boolean) {
        _gyroMode.value = on
        prefs.edit().putBoolean(KEY_GYRO_MODE, on).apply()
    }

    fun setGyroSpeed(speed: Float) {
        val value = speed.coerceIn(POINTER_SPEED_MIN, POINTER_SPEED_MAX)
        _gyroSpeed.value = value
        prefs.edit().putFloat(KEY_GYRO_SPEED, value).apply()
    }

    fun setGyroBias(bias: FloatArray) {
        prefs.edit().putString(KEY_GYRO_BIAS, bias.joinToString(",")).apply()
    }

    fun pointerMove(dx: Float, dy: Float) {
        pointerEvents.trySend(PointerEvent.Move(dx, dy))
    }

    fun pointerScroll(dx: Float, dy: Float) {
        pointerEvents.trySend(PointerEvent.Scroll(dx, dy))
    }

    fun pointerClick() {
        pointerEvents.trySend(PointerEvent.Click)
    }

    fun connect(ip: String, port: Int = DEFAULT_PORT) = connect(ip, port, tv = null, auto = false)

    fun connect(tv: DiscoveredTv) = connect(tv.host, savedPort, tv = tv, auto = false)

    private fun connect(ip: String, port: Int, tv: DiscoveredTv?, auto: Boolean) {
        val host = ip.trim()
        if (host.isEmpty()) return
        // A tap on another TV wins over an auto-connect that is still in flight.
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            _connectionState.value = ConnectionState.CONNECTING
            _connectionError.value = null
            _status.value = if (auto) "Kayıtlı TV'ye bağlanılıyor…" else null
            val result = attemptConnect(host, port, tv)
            ensureActive()
            result
                .onSuccess { kind -> onConnected(host, port, kind, tv) }
                .onFailure { e ->
                    _status.value = null
                    if (auto) {
                        // Quiet failure: the TV is probably off; the list and the power button stay available.
                        _connectionState.value = ConnectionState.DISCONNECTED
                    } else {
                        _connectionState.value = ConnectionState.ERROR
                        _connectionError.value = describeConnectError(e)
                    }
                }
        }
    }

    private suspend fun attemptConnect(host: String, port: Int, tv: DiscoveredTv?): Result<TvKind> =
        when (detectBackend(host, port)) {
            TvKind.WebOs -> connectWebOs(host, tv?.uuid ?: _savedTv.value?.takeIf { it.host == host }?.uuid)
            TvKind.Adb -> adbManager.connect(host, port).map { TvKind.Adb }
            null -> Result.failure(SocketTimeoutException())
        }

    // quiet = silent reconnect after a drop: no pop-ups on the TV or the phone.
    private suspend fun onConnected(host: String, port: Int, kind: TvKind, tv: DiscoveredTv?, quiet: Boolean = false) {
        currentHost = host
        currentPort = port
        autoConnectArmed = false
        _status.value = null
        _connectionError.value = null
        prefs.edit().putString(KEY_HOST, host).putInt(KEY_PORT, port).apply()
        rememberTv(host, kind, tv)
        _backend.value = kind
        _connectionState.value = ConnectionState.CONNECTED
        if (kind == TvKind.WebOs) {
            loadWebOsInfo(tv?.name)
            startWebOsExtras()
        } else {
            loadAdbInfo()
        }
        val hadLink = _pendingLink.value != null
        _pendingLink.value?.let { link ->
            _pendingLink.value = null
            openOnTv(link)
        }
        developerAssist(kind, autoTv = !quiet && !hadLink, autoPhone = !quiet)
    }

    private fun phoneDevEnabled(): Boolean = runCatching {
        Settings.Global.getInt(getApplication<Application>().contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
    }.getOrDefault(false)

    private suspend fun checkTvDev(): DevState {
        val host = currentHost ?: return DevState.Unknown
        val factory = lanSocketFactory()
        return withContext(Dispatchers.IO) {
            if (TvScanner.isOpen(host, DEV_SSH_PORT, DEV_CHECK_TIMEOUT_MS, factory)) DevState.On else DevState.Off
        }
    }

    fun refreshDevStatus() {
        _phoneDev.value = phoneDevEnabled()
        if (webOs == null) return
        viewModelScope.launch { _tvDev.value = checkTvDev() }
    }

    fun setDevAuto(on: Boolean) {
        prefs.edit().putBoolean(KEY_DEV_AUTO, on).apply()
        _devAuto.value = on
    }

    // Opens whatever is needed to switch Developer Mode on. Neither side can be switched on by an app:
    // the TV wants the LG developer login, and Android only lets the user enable developer options.
    private fun developerAssist(kind: TvKind, autoTv: Boolean, autoPhone: Boolean) {
        viewModelScope.launch {
            _phoneDev.value = phoneDevEnabled()
            if (kind == TvKind.WebOs) _tvDev.value = checkTvDev()
            if (!_devAuto.value) return@launch
            if (kind == TvKind.WebOs && autoTv && _tvDev.value == DevState.Off) openDeveloperMode()
            // The phone screen is shown once per install; after that the Apps tab card has the button.
            if (autoPhone && !_phoneDev.value && !prefs.getBoolean(KEY_DEV_PHONE_PROMPTED, false)) {
                prefs.edit().putBoolean(KEY_DEV_PHONE_PROMPTED, true).apply()
                openPhoneDeveloperSettings()
            }
        }
    }

    fun openPhoneDeveloperSettings() {
        val app = getApplication<Application>()
        // The developer screen only exists once the options are on; before that, "About phone" has the build number.
        val action = if (phoneDevEnabled()) Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS else Settings.ACTION_DEVICE_INFO_SETTINGS
        val opened = runCatching { app.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (!opened) runCatching { app.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    // After the Developer Mode app is opened, wait for the user to switch it on at the TV.
    private fun watchTvDevMode() {
        devWatchJob?.cancel()
        devWatchJob = viewModelScope.launch {
            val started = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - started < DEV_WATCH_MS) {
                delay(3_000)
                if (webOs == null) return@launch
                if (checkTvDev() == DevState.On) {
                    _tvDev.value = DevState.On
                    _remoteNotice.value = Notice("TV'de Developer Mode açıldı ✓")
                    return@launch
                }
            }
        }
    }

    // Same IP field for both kinds of TV: whichever service answers decides the protocol.
    private suspend fun detectBackend(host: String, adbPort: Int): TvKind? = withContext(Dispatchers.IO) {
        val factory = lanSocketFactory()
        coroutineScope {
            val plain = async { TvScanner.isOpen(host, TvScanner.WEBOS_PORT, DETECT_TIMEOUT_MS, factory) }
            val secure = async { TvScanner.isOpen(host, TvScanner.WEBOS_SECURE_PORT, DETECT_TIMEOUT_MS, factory) }
            val adb = async { TvScanner.isOpen(host, adbPort, DETECT_TIMEOUT_MS, factory) }
            when {
                plain.await() || secure.await() -> TvKind.WebOs
                adb.await() -> TvKind.Adb
                else -> null
            }
        }
    }

    private fun lanSocketFactory(): SocketFactory? = TvScanner.lan(getApplication())?.network?.socketFactory

    private suspend fun connectWebOs(host: String, uuid: String?): Result<TvKind> {
        val client = WebOsClient(host, lanSocketFactory()) { error -> viewModelScope.launch { onConnectionLost(error) } }
        return try {
            // The key belongs to the TV, not the address: try the uuid's key when the IP changed.
            val savedKey = uuid?.let { prefs.getString(webOsUuidKeyFor(it), null) }
                ?: prefs.getString(webOsKeyFor(host), null)
                ?: prefs.getString(KEY_LAST_WEBOS_KEY, null)
            val key = client.connect(savedKey) { _pairingPrompt.value = true }
            prefs.edit()
                .putString(webOsKeyFor(host), key)
                .putString(KEY_LAST_WEBOS_KEY, key)
                .apply { if (uuid != null) putString(webOsUuidKeyFor(uuid), key) }
                .apply()
            webOs = client
            Result.success(TvKind.WebOs)
        } catch (e: CancellationException) {
            client.close()
            throw e
        } catch (e: Throwable) {
            client.close()
            Result.failure(e)
        } finally {
            _pairingPrompt.value = false
        }
    }

    private fun webOsKeyFor(host: String) = "webos_key_$host"

    private fun webOsUuidKeyFor(uuid: String) = "webos_key_uuid_$uuid"

    private suspend fun loadAdbInfo() {
        adbManager.getDeviceInfo().onSuccess { _deviceInfo.value = it }
        adbManager.displaySize().onSuccess { tvSize = it }
    }

    private suspend fun loadWebOsInfo(friendlyName: String?) {
        val tv = webOs ?: return
        val software = runCatching { tv.softwareInfo() }.getOrNull()
        val model = tv.systemInfo?.optString("modelName").orEmpty()
            .ifBlank { software?.optString("model_name").orEmpty() }
            .ifBlank { "webOS TV" }
        // product_name is e.g. "webOSTV 8.0"; major_ver is the firmware version, not the webOS one.
        val platform = software?.optString("product_name").orEmpty()
            .replace("webOSTV", "webOS").trim()
            .ifBlank { "webOS" }
        val name = friendlyName ?: _savedTv.value?.name
        _deviceInfo.value = DeviceInfo(
            model = name ?: model,
            manufacturer = "",
            androidVersion = "",
            apiLevel = 0,
            platform = if (name != null && name != model) "$platform · $model" else platform
        )
        if (name == null) _savedTv.value?.let { saveTv(it.copy(name = model)) }
    }

    private fun startWebOsExtras() {
        val tv = webOs ?: return
        tv.subscribeVolume { level, isMuted ->
            _volume.value = level
            _muted.value = isMuted
        }
        viewModelScope.launch {
            runCatching { tv.macAddresses() }.onSuccess { macs ->
                val saved = _savedTv.value
                if (macs.isNotEmpty() && saved != null) saveTv(saved.copy(macs = macs))
            }
        }
    }

    private fun rememberTv(host: String, kind: TvKind, tv: DiscoveredTv?) {
        val previous = _savedTv.value
        val sameTv = previous != null && (
            (tv?.uuid != null && tv.uuid == previous.uuid) || (previous.host == host && previous.kind == kind)
            )
        saveTv(
            SavedTv(
                host = host,
                name = tv?.name ?: previous?.name?.takeIf { sameTv },
                kind = kind,
                uuid = tv?.uuid ?: previous?.uuid?.takeIf { sameTv },
                macs = previous?.macs?.takeIf { sameTv }.orEmpty()
            )
        )
    }

    private fun saveTv(tv: SavedTv) {
        _savedTv.value = tv
        prefs.edit()
            .putString(KEY_LAST_HOST, tv.host)
            .putString(KEY_LAST_NAME, tv.name)
            .putString(KEY_LAST_KIND, tv.kind.name)
            .putString(KEY_LAST_UUID, tv.uuid)
            .putString(KEY_LAST_MACS, tv.macs.joinToString(","))
            .apply()
    }

    private fun loadSavedTv(): SavedTv? {
        val host = prefs.getString(KEY_LAST_HOST, null) ?: return null
        val kind = runCatching { TvKind.valueOf(prefs.getString(KEY_LAST_KIND, null).orEmpty()) }.getOrNull() ?: return null
        return SavedTv(
            host = host,
            name = prefs.getString(KEY_LAST_NAME, null),
            kind = kind,
            uuid = prefs.getString(KEY_LAST_UUID, null),
            macs = prefs.getString(KEY_LAST_MACS, null).orEmpty().split(',').filter { it.isNotBlank() }
        )
    }

    // User pressed "disconnect": stop everything, including any auto-connect.
    fun disconnect() {
        autoConnectArmed = false
        connectJob?.cancel()
        teardown()
        _status.value = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun teardown() {
        // Stop the streams first so they end quietly instead of reporting a dropped connection.
        mirror.stop()
        logcat.stop()
        webOs?.close()
        webOs = null
        adbManager.disconnect()
        while (commands.tryReceive().isSuccess) Unit
        while (pointerEvents.tryReceive().isSuccess) Unit
        _backend.value = null
        _inputs.value = null
        _volume.value = null
        _muted.value = false
        _tvDev.value = DevState.Unknown
        devWatchJob?.cancel()
        tvSize = null
        files.reset()
        _deviceInfo.value = null
        _apps.value = emptyList()
        _screenshot.value = null
        _terminalHistory.value = emptyList()
        _connectionError.value = null
        _install.value = null
        _installMessage.value = null
        _remoteNotice.value = null
    }

    private fun onConnectionLost(error: Throwable) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        val host = currentHost
        val port = currentPort
        teardown()
        if (SystemClock.elapsedRealtime() - lastPowerOffAt < POWER_OFF_GRACE_MS) {
            _connectionState.value = ConnectionState.DISCONNECTED
            _status.value = "TV kapatıldı"
            return
        }
        if (host == null) {
            _connectionState.value = ConnectionState.ERROR
            _connectionError.value = "Bağlantı koptu: ${error.message ?: error.javaClass.simpleName}"
            return
        }
        reconnect(host, port, error)
    }

    // Wi-Fi blips and TV standby drop the socket; try the same TV again before giving up.
    private fun reconnect(host: String, port: Int, error: Throwable) {
        connectJob?.cancel()
        _connectionState.value = ConnectionState.CONNECTING
        _status.value = "Bağlantı koptu, yeniden bağlanılıyor…"
        connectJob = viewModelScope.launch {
            for (wait in RECONNECT_DELAYS_MS) {
                delay(wait)
                val result = attemptConnect(host, port, null)
                ensureActive()
                if (result.isSuccess) {
                    onConnected(host, port, result.getOrThrow(), null, quiet = true)
                    return@launch
                }
            }
            _status.value = null
            _connectionState.value = ConnectionState.ERROR
            _connectionError.value = "Bağlantı koptu: ${error.message ?: error.javaClass.simpleName}"
            // The TV may be back under a new address: let the scan find it by uuid and reconnect.
            autoConnectArmed = true
            scanNetwork()
        }
    }

    private fun busy(): Boolean =
        _connectionState.value == ConnectionState.CONNECTED || connectJob?.isActive == true

    /** Called whenever the connect screen appears: scan, and reconnect to the known TV if possible. */
    fun onConnectScreenShown() {
        if (busy()) return
        if (_scanState.value != ScanState.Scanning) scanNetwork()
        val saved = _savedTv.value ?: return
        if (!autoConnectArmed || directAttempted) return
        directAttempted = true
        viewModelScope.launch {
            // Same address as last time: this connects before the sweep has even finished.
            val kind = detectBackend(saved.host, savedPort)
            if (kind != null && autoConnectArmed && !busy()) connect(saved.host, savedPort, tv = null, auto = true)
        }
    }

    fun scanNetwork() {
        if (_scanState.value == ScanState.Scanning) return
        val lan = TvScanner.lan(getApplication())
        if (lan == null) {
            _scanState.value = ScanState.NoWifi
            return
        }
        _discovered.value = emptyList()
        _scanState.value = ScanState.Scanning
        scanJob = viewModelScope.launch {
            val lock = multicastLock()
            try {
                coroutineScope {
                    launch {
                        runCatching {
                            Ssdp.search(configure = { socket -> runCatching { lan.network.bindSocket(socket) } }) { reply ->
                                if (reply.isWebOs) onSsdpReply(reply)
                            }
                        }
                    }
                    launch {
                        TvScanner.probe(TvScanner.candidateHosts(lan.subnet), lan.network.socketFactory) { host, kind ->
                            onPortFound(host, kind)
                        }
                    }
                }
            } finally {
                runCatching { lock?.release() }
            }
            _scanState.value = ScanState.Done
            onScanFinished()
        }
    }

    private fun multicastLock(): WifiManager.MulticastLock? = runCatching {
        val wifi = getApplication<Application>().applicationContext.getSystemService(WifiManager::class.java)
        wifi?.createMulticastLock("tvremote-ssdp")?.apply {
            setReferenceCounted(false)
            acquire()
        }
    }.getOrNull()

    private fun onSsdpReply(reply: Ssdp.Reply) {
        val name = reply.friendlyName
        _discovered.update { found ->
            val existing = found.firstOrNull { it.host == reply.host }
            val merged = (existing ?: DiscoveredTv(reply.host, null, TvKind.WebOs)).copy(
                kind = TvKind.WebOs,
                // SSDP names beat reverse-DNS guesses.
                name = name ?: existing?.name,
                uuid = reply.uuid ?: existing?.uuid
            )
            sortHosts(found.filter { it.host != reply.host } + merged)
        }
        if (name == null) {
            reply.location?.let { location ->
                viewModelScope.launch(Dispatchers.IO) {
                    val (friendly, model) = Ssdp.describe(location)
                    if (friendly != null || model != null) {
                        _discovered.update { found ->
                            found.map { if (it.host == reply.host) it.copy(name = friendly ?: it.name, model = model ?: it.model) else it }
                        }
                    }
                }
            }
        }
        viewModelScope.launch { maybeAutoConnect(reply.host) }
    }

    private fun onPortFound(host: String, kind: TvKind) {
        var added = false
        _discovered.update { found ->
            val existing = found.firstOrNull { it.host == host }
            when {
                existing == null -> {
                    added = true
                    sortHosts(found + DiscoveredTv(host, null, kind))
                }
                // A webOS port is the more specific signal than a stray ADB port.
                kind == TvKind.WebOs && existing.kind != TvKind.WebOs ->
                    found.map { if (it.host == host) it.copy(kind = TvKind.WebOs) else it }
                else -> found
            }
        }
        if (added) {
            viewModelScope.launch(Dispatchers.IO) {
                TvScanner.hostName(host)?.let { name ->
                    _discovered.update { found -> found.map { if (it.host == host && it.name == null) it.copy(name = name) else it } }
                }
            }
        }
        viewModelScope.launch { maybeAutoConnect(host) }
    }

    private fun sortHosts(list: List<DiscoveredTv>) =
        list.sortedBy { it.host.substringAfterLast('.').toIntOrNull() ?: 0 }

    private fun maybeAutoConnect(host: String) {
        if (!autoConnectArmed || busy()) return
        val saved = _savedTv.value ?: return
        val tv = _discovered.value.firstOrNull { it.host == host } ?: return
        val same = if (saved.uuid != null && tv.uuid != null) saved.uuid == tv.uuid else saved.host == tv.host
        if (same) connect(tv.host, savedPort, tv = tv, auto = true)
    }

    private fun onScanFinished() {
        if (!autoConnectArmed || busy()) return
        val found = _discovered.value
        // First run with exactly one TV on the network: no need to make the user tap it.
        if (_savedTv.value == null && found.size == 1) connect(found.single().host, savedPort, tv = found.single(), auto = true)
    }

    /** Wake-on-LAN for the saved TV, then wait for its service to come up and connect. */
    fun powerOn() {
        val saved = _savedTv.value ?: return
        if (saved.macs.isEmpty()) return
        val lan = TvScanner.lan(getApplication())
        connectJob?.cancel()
        _connectionError.value = null
        _connectionState.value = ConnectionState.CONNECTING
        _status.value = "TV açılıyor…"
        connectJob = viewModelScope.launch {
            val broadcasts = listOfNotNull(
                runCatching { InetAddress.getByName("255.255.255.255") }.getOrNull(),
                lan?.subnet?.let { runCatching { InetAddress.getByName(it.broadcast) }.getOrNull() }
            )
            val wake: suspend () -> Unit = {
                WakeOnLan.send(saved.macs, broadcasts) { socket -> lan?.network?.let { runCatching { it.bindSocket(socket) } } }
            }
            wake()
            var kind: TvKind? = null
            val started = SystemClock.elapsedRealtime()
            var lastWake = started
            while (SystemClock.elapsedRealtime() - started < POWER_ON_TIMEOUT_MS) {
                kind = detectBackend(saved.host, savedPort)
                if (kind != null) break
                if (SystemClock.elapsedRealtime() - lastWake > WAKE_REPEAT_MS) {
                    wake()
                    lastWake = SystemClock.elapsedRealtime()
                }
                delay(1_000)
            }
            ensureActive()
            if (kind == null) {
                _connectionState.value = ConnectionState.DISCONNECTED
                _status.value = "TV açılmadı. TV ayarlarında \"Wi-Fi ile aç\" / \"Mobil cihazla aç\" açık olmalı."
                autoConnectArmed = true
                scanNetwork()
                return@launch
            }
            _status.value = "TV açıldı, bağlanılıyor…"
            val result = attemptConnect(saved.host, savedPort, null)
            ensureActive()
            result
                .onSuccess { connected ->
                    onConnected(saved.host, savedPort, connected, null)
                    // A TV in network standby accepts the connection with the panel still dark.
                    webOs?.let { tv ->
                        val state = tv.powerState().orEmpty()
                        if (state.contains("Standby", ignoreCase = true) || state.contains("Suspend", ignoreCase = true)) {
                            runCatching { tv.request("system/turnOn") }
                        }
                    }
                }
                .onFailure { e ->
                    _status.value = null
                    _connectionState.value = ConnectionState.ERROR
                    _connectionError.value = describeConnectError(e)
                }
        }
    }

    /** Opens a shared link on the TV now, or right after the next connection. */
    fun openOnTv(rawUrl: String) {
        val url = rawUrl.trim().let { if (it.contains("://")) it else "https://$it" }
        val tv = webOs
        when {
            tv != null -> viewModelScope.launch {
                runCatching { tv.openUrl(url) }
                    .onSuccess { _remoteNotice.value = Notice("TV'de açıldı: $url") }
                    .onFailure { _remoteNotice.value = Notice("Açılamadı: ${it.message ?: url}", isError = true) }
            }
            _backend.value == TvKind.Adb -> viewModelScope.launch {
                adbManager.shell("am start -a android.intent.action.VIEW -d ${shellQuote(url)}")
                    .onSuccess { _remoteNotice.value = Notice("TV'de açıldı: $url") }
                    .onFailure { _remoteNotice.value = Notice("Açılamadı: ${it.message ?: url}", isError = true) }
            }
            else -> _pendingLink.value = url
        }
    }

    fun clearPendingLink() {
        _pendingLink.value = null
    }

    fun clearRemoteNotice() {
        _remoteNotice.value = null
    }

    fun sendButton(name: String) {
        commands.trySend(RemoteCommand.Button(name))
    }

    // Opens LG's Developer Mode app on the TV; it has to be installed from the TV's Content Store first.
    fun openDeveloperMode() {
        val tv = webOs ?: return
        viewModelScope.launch {
            val app = runCatching { WebOsClient.findDeveloperMode(tv.launchPoints()) }.getOrNull()
            if (app == null) {
                _remoteNotice.value = Notice(
                    "TV'de Developer Mode uygulaması yüklü değil. TV'deki LG Content Store'da \"Developer Mode\" arayıp yükle.",
                    isError = true
                )
                return@launch
            }
            runCatching { tv.launch(app.id) }
                .onSuccess {
                    _remoteNotice.value = Notice("TV'de ${app.title} açıldı: giriş yapıp Dev Mode Status'u aç")
                    watchTvDevMode()
                }
                .onFailure { _remoteNotice.value = Notice("Açılamadı: ${it.message ?: app.title}", isError = true) }
        }
    }

    fun toggleScreen() {
        val tv = webOs ?: return
        viewModelScope.launch {
            runCatching { tv.setScreen(on = tv.powerState() == "Screen Off") }
                .onFailure { _remoteNotice.value = Notice("TV ekran kapatmayı desteklemiyor", isError = true) }
        }
    }

    fun sendKey(keyCode: Int) {
        if (keyCode == TvKeyCode.POWER) lastPowerOffAt = SystemClock.elapsedRealtime()
        commands.trySend(RemoteCommand.Key(keyCode))
    }

    // submit = the keyboard's enter key: type the text, then confirm it (e.g. run a search).
    fun inputText(text: String, submit: Boolean = false) {
        if (text.isNotEmpty() || submit) commands.trySend(RemoteCommand.Text(text, submit))
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
                .onFailure { sendKey(TvKeyCode.TV_INPUT) }
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
        connectJob?.cancel()
        scanJob?.cancel()
        devWatchJob?.cancel()
        mirror.stop()
        logcat.stop()
        webOs?.close()
        adbManager.disconnect()
    }

    private companion object {
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_LAST_HOST = "last_tv_host"
        const val KEY_LAST_NAME = "last_tv_name"
        const val KEY_LAST_KIND = "last_tv_kind"
        const val KEY_LAST_UUID = "last_tv_uuid"
        const val KEY_LAST_MACS = "last_tv_macs"
        const val KEY_LAST_WEBOS_KEY = "webos_last_key"
        const val KEY_DEV_AUTO = "dev_auto"
        const val KEY_POINTER_SPEED = "pointer_speed"
        const val KEY_LIGHT_THEME = "light_theme"
        const val KEY_GYRO_MODE = "gyro_mode"
        const val KEY_GYRO_SPEED = "gyro_speed"
        const val KEY_GYRO_BIAS = "gyro_bias"
        const val POINTER_SPEED_MIN = 0.5f
        const val POINTER_SPEED_MAX = 3f
        const val KEY_DEV_PHONE_PROMPTED = "dev_phone_prompted"
        const val DEV_SSH_PORT = 9922
        const val DEV_CHECK_TIMEOUT_MS = 1_200
        const val DEV_WATCH_MS = 180_000L
        const val DEFAULT_PORT = 5555
        const val MAX_KEY_BATCH = 16
        const val KEYCODE_ENTER = 66
        const val MIRROR_LONG_SIDE = 1280
        const val DETECT_TIMEOUT_MS = 1500
        const val POINTER_INTERVAL_MS = 8L
        const val POWER_OFF_GRACE_MS = 15_000L
        const val POWER_ON_TIMEOUT_MS = 30_000L
        const val WAKE_REPEAT_MS = 5_000L
        val RECONNECT_DELAYS_MS = listOf(600L, 2_000L, 5_000L)
    }
}
