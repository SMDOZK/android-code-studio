package com.smdozk.tvremote.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smdozk.tvremote.adb.AdbManager
import com.smdozk.tvremote.model.AppInfo
import com.smdozk.tvremote.model.ConnectionState
import com.smdozk.tvremote.model.DeviceInfo
import com.smdozk.tvremote.model.TerminalEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val adbManager = AdbManager(application.filesDir)

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

    private val _terminalHistory = MutableStateFlow<List<TerminalEntry>>(emptyList())
    val terminalHistory: StateFlow<List<TerminalEntry>> = _terminalHistory.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _appsFilter = MutableStateFlow(true)
    val appsFilter: StateFlow<Boolean> = _appsFilter.asStateFlow()

    fun connect(ip: String, port: Int = 5555) {
        viewModelScope.launch {
            _connectionState.value = ConnectionState.CONNECTING
            _connectionError.value = null
            adbManager.connect(ip, port)
                .onSuccess {
                    _connectionState.value = ConnectionState.CONNECTED
                    adbManager.getDeviceInfo().onSuccess { _deviceInfo.value = it }
                }
                .onFailure { e ->
                    _connectionState.value = ConnectionState.ERROR
                    _connectionError.value = e.message ?: "Bağlantı başarısız"
                }
        }
    }

    fun disconnect() {
        adbManager.disconnect()
        _connectionState.value = ConnectionState.DISCONNECTED
        _deviceInfo.value = null
        _apps.value = emptyList()
        _screenshot.value = null
        _terminalHistory.value = emptyList()
        _connectionError.value = null
    }

    fun sendKey(keyCode: Int) {
        viewModelScope.launch { adbManager.sendKey(keyCode) }
    }

    fun inputText(text: String) {
        viewModelScope.launch { adbManager.inputText(text) }
    }

    fun loadApps(thirdPartyOnly: Boolean = _appsFilter.value) {
        viewModelScope.launch {
            _appsFilter.value = thirdPartyOnly
            _isLoadingApps.value = true
            adbManager.listPackages(thirdPartyOnly)
                .onSuccess { _apps.value = it }
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
        viewModelScope.launch {
            _isCapturing.value = true
            adbManager.screenshot(getApplication<Application>().cacheDir)
                .onSuccess { _screenshot.value = it }
                .onFailure { e ->
                    addTerminalEntry("screencap", "Hata: ${e.message}", isError = true)
                }
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
}
