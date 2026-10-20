package com.smdozk.tvremote.model

data class AppInfo(
    val packageName: String,
    val isSystem: Boolean = false,
    val title: String? = null
) {
    val displayName: String get() = title ?: KNOWN_APP_NAMES[packageName] ?: deriveAppName(packageName)
}

private val KNOWN_APP_NAMES = mapOf(
    "com.netflix.ninja" to "Netflix",
    "com.google.android.youtube.tv" to "YouTube",
    "com.google.android.youtube.tvkids" to "YouTube Kids",
    "com.google.android.youtube.tvmusic" to "YouTube Music",
    "com.amazon.amazonvideo.livingroom" to "Prime Video",
    "com.disney.disneyplus" to "Disney+",
    "com.spotify.tv.android" to "Spotify",
    "org.xbmc.kodi" to "Kodi",
    "com.teamsmart.videomanager.tv" to "SmartTube",
    "com.liskovsoft.smarttubetv" to "SmartTube",
    "com.liskovsoft.smarttubetv.beta" to "SmartTube Beta",
    "com.plexapp.android" to "Plex",
    "org.videolan.vlc" to "VLC",
    "com.android.vending" to "Play Store",
    "com.android.chrome" to "Chrome",
    "com.google.android.tvlauncher" to "TV Launcher",
    "com.google.android.apps.tv.launcherx" to "Google TV",
    "com.google.android.katniss" to "Google Asistan",
    "com.android.tv.settings" to "TV Ayarları",
)

private val GENERIC_NAME_TOKENS = setOf(
    "android", "androidtv", "atv", "tv", "app", "apps", "mobile", "leanback", "client", "free", "pro", "lite", "ad"
)

// Package names are all we get over ADB; skip the TLD and filler words to find the product name.
internal fun deriveAppName(packageName: String): String {
    val tokens = packageName.split('.').filter { it.isNotBlank() }
    val meaningful = tokens.drop(1).filter { it.lowercase() !in GENERIC_NAME_TOKENS }
    val name = meaningful.lastOrNull() ?: tokens.lastOrNull() ?: packageName
    return if (name.length <= 3) name.uppercase() else name.replaceFirstChar { it.uppercase() }
}

data class DeviceInfo(
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val apiLevel: Int,
    val platform: String = "Android $androidVersion"
) {
    val displayName: String get() = "$manufacturer $model".trim()
}

enum class TvKind { WebOs, Adb }

data class TvInput(val id: String, val label: String)

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

data class RemoteFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedSeconds: Long
) {
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
}

enum class TransferKind { Upload, Download, Install }

enum class TransferStage { Running, Finishing, Done }

data class Transfer(
    val title: String,
    val kind: TransferKind,
    val bytes: Long,
    val total: Long,
    val stage: TransferStage = TransferStage.Running
) {
    val progress: Float? get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else null
}

data class LogLine(
    val id: Long,
    val level: Char,
    val tag: String,
    val message: String
)

data class Notice(val text: String, val isError: Boolean = false)

data class DiscoveredTv(
    val host: String,
    val name: String?,
    val kind: TvKind
)
