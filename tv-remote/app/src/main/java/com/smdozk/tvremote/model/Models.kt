package com.smdozk.tvremote.model

data class AppInfo(
    val packageName: String,
    val isSystem: Boolean = false
) {
    val displayName: String get() = packageName.substringAfterLast('.')
}

data class DeviceInfo(
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val apiLevel: Int
) {
    val displayName: String get() = "$manufacturer $model"
}

data class TerminalEntry(
    val command: String,
    val output: String,
    val isError: Boolean = false
)

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}
