package com.smdozk.tvremote.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smdozk.tvremote.adb.AdbManager
import com.smdozk.tvremote.model.AppInfo
import com.smdozk.tvremote.model.ConnectionState
import com.smdozk.tvremote.model.DeviceInfo
import com.smdozk.tvremote.model.TerminalEntry
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val adbManager = AdbManager(application.filesDir)
    private val prefs = application.getSharedPreferences("tv_remote", Context.MODE_PRIVATE)

    val savedHost: String get() = prefs.getString(KEY_HOST, null) ?: "192.168.1."
    val savedPort: Int get() = prefs.getInt(KEY_PORT, 5555)

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

    private val _screenshot = MutableStateFlow<Bitmap?>(null)
    val screenshot: StateFlow<Bitmap?> = _screenshot.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _screenshotError = MutableStateFlow<String?>(null)
    val screenshotError: StateFlow<String?> = _screenshotError.asStateFlow()

    private val _terminalHistory = MutableStateFlow<List<TerminalEntry>>(emptyList())
    val terminalHistory: StateFlow<List<TerminalEntry>> = _terminalHistory.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _appsFilter = MutableStateFlow(true)
    val appsFilter: StateFlow<Boolean> = _appsFilter.asStateFlow()

    private sealed interface RemoteCommand {
        data class Key(val code: Int) : RemoteCommand
        data class Text(val text: String) : RemoteCommand
    }

    // A single consumer keeps keys and text in press order; parallel shells would reorder them.
    private val commands = Channel<RemoteCommand>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        viewModelScope.launch {
            var carried: RemoteCommand? = null
            while (true) {
                val command = carried ?: commands.receive()
                carried = null
                val result = when (command) {
                    is RemoteCommand.Text -> adbManager.inputText(command.text)
                    is RemoteCommand.Key -> {
                        val batch = mutableListOf(command.code)
                        while (batch.size < MAX_KEY_BATCH) {
                            val next = commands.tryReceive().getOrNull() ?: break
                            if (next is RemoteCommand.Key) batch += next.code else { carried = next; break }
                        }
                        adbManager.sendKeys(batch)
                    }
                }
                result.onFailure(::onConnectionLost)
            }
        }
    }

    fun connect(ip: String, port: Int = 5555) {
        val host = ip.trim()
        viewModelScope.launch {
            _connectionState.value = ConnectionState.CONNECTING
            _connectionError.value = null
            adbManager.connect(host, port)
                .onSuccess {
                    prefs.edit().putString(KEY_HOST, host).putInt(KEY_PORT, port).apply()
                    _connectionState.value = ConnectionState.CONNECTED
                    adbManager.getDeviceInfo().onSuccess { _deviceInfo.value = it }
                }
                .onFailure { e ->
                    _connectionState.value = ConnectionState.ERROR
                    _connectionError.value = describeConnectError(e)
                }
        }
    }

    fun disconnect() {
        adbManager.disconnect()
        while (commands.tryReceive().isSuccess) Unit
        _connectionState.value = ConnectionState.DISCONNECTED
        _deviceInfo.value = null
        _apps.value = emptyList()
        _screenshot.value = null
        _terminalHistory.value = emptyList()
        _connectionError.value = null
    }

    private fun describeConnectError(error: Throwable): String = when (error) {
        is ConnectException -> "TV bağlantıyı reddetti. TV'de ADB / USB hata ayıklama açık mı?"
        is SocketTimeoutException, is NoRouteToHostException -> "TV'ye ulaşılamadı. IP adresini ve aynı Wi-Fi ağında olduğunuzu kontrol edin."
        is UnknownHostException -> "Geçersiz IP adresi."
        else -> error.message ?: error.javaClass.simpleName
    }

    private fun onConnectionLost(error: Throwable) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        disconnect()
        _connectionState.value = ConnectionState.ERROR
        _connectionError.value = "Bağlantı koptu: ${error.message ?: error.javaClass.simpleName}"
    }

    fun sendKey(keyCode: Int) {
        commands.trySend(RemoteCommand.Key(keyCode))
    }

    fun inputText(text: String) {
        if (text.isNotEmpty()) commands.trySend(RemoteCommand.Text(text))
    }

    fun loadApps(thirdPartyOnly: Boolean = _appsFilter.value) {
        viewModelScope.launch {
            _appsFilter.value = thirdPartyOnly
            _isLoadingApps.value = true
            adbManager.listPackages(thirdPartyOnly).onSuccess { _apps.value = it }
            _isLoadingApps.value = false
        }
    }

    fun launchApp(packageName: String) {
        viewModelScope.launch { adbManager.launchApp(packageName) }
    }

    fun stopApp(packageName: String) {
        viewModelScope.launch { adbManager.stopApp(packageName) }
    }

    fun uninstallApp(packageName: String) {
        viewModelScope.launch {
            adbManager.uninstallApp(packageName).onSuccess { loadApps() }
        }
    }

    fun captureScreenshot() {
        if (_isCapturing.value) return
        viewModelScope.launch {
            _isCapturing.value = true
            _screenshotError.value = null
            adbManager.screenshot()
                .onSuccess { _screenshot.value = it }
                .onFailure { e -> _screenshotError.value = e.message ?: e.javaClass.simpleName }
            _isCapturing.value = false
        }
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
        adbManager.disconnect()
    }

    private companion object {
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val MAX_KEY_BATCH = 16
    }
}
