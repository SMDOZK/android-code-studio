package com.smdozk.tvremote.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Turkish = Locale("tr", "TR")
private val SIZE_UNITS = listOf("KB", "MB", "GB", "TB")

fun formatSize(bytes: Long): String {
    if (bytes < 0) return "?"
    if (bytes < 1024) return "$bytes B"
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < SIZE_UNITS.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Turkish, if (value < 10) "%.1f %s" else "%.0f %s", value, SIZE_UNITS[unit])
}

fun formatDate(epochSeconds: Long): String =
    SimpleDateFormat("d MMM yyyy HH:mm", Turkish).format(Date(epochSeconds * 1000))
