package com.smdozk.tvremote.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

// Magic packet: 6 x 0xFF then the MAC 16 times. The TV must allow "turn on via Wi-Fi/network".
object WakeOnLan {

    fun packet(mac: String): ByteArray {
        val bytes = mac.split(':', '-').map { it.toInt(16).toByte() }
        require(bytes.size == 6) { "Geçersiz MAC: $mac" }
        return ByteArray(6) { 0xFF.toByte() } + List(16) { bytes }.flatten()
    }

    suspend fun send(
        macs: List<String>,
        broadcasts: List<InetAddress>,
        configure: (DatagramSocket) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val packets = macs.mapNotNull { runCatching { packet(it) }.getOrNull() }
        if (packets.isEmpty()) return@withContext
        DatagramSocket().use { socket ->
            socket.broadcast = true
            configure(socket)
            repeat(3) {
                for (data in packets) for (address in broadcasts) for (port in PORTS) {
                    runCatching { socket.send(DatagramPacket(data, data.size, address, port)) }
                }
                delay(120)
            }
        }
    }

    private val PORTS = listOf(9, 7)
}
