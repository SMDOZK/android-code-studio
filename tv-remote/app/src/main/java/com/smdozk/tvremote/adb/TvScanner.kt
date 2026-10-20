package com.smdozk.tvremote.adb

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.smdozk.tvremote.model.TvKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

object TvScanner {

    data class Subnet(val address: Int, val prefixLength: Int) {
        val broadcast: String
            get() {
                val mask = if (prefixLength == 0) 0 else -1 shl (32 - prefixLength)
                return toDotted((address and mask) or mask.inv())
            }
    }

    // The Wi-Fi (or Ethernet) network the TV lives on, plus our address in it.
    data class Lan(val network: Network, val subnet: Subnet)

    fun lan(context: Context): Lan? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return null
        // Not activeNetwork: with a VPN on, that is the tunnel rather than the LAN the TV is on.
        @Suppress("DEPRECATION")
        return connectivity.allNetworks
            .filter { network ->
                val capabilities = connectivity.getNetworkCapabilities(network)
                capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                    )
            }
            .firstNotNullOfOrNull { network ->
                connectivity.getLinkProperties(network)?.linkAddresses
                    ?.firstOrNull { it.address is Inet4Address }
                    ?.let { Lan(network, Subnet(toInt(it.address.address), it.prefixLength)) }
            }
    }

    fun wifiSubnet(context: Context): Subnet? = lan(context)?.subnet

    // Large subnets are clamped to the /24 around the phone so a sweep stays a couple of seconds.
    fun candidateHosts(subnet: Subnet): List<String> {
        val prefix = subnet.prefixLength.coerceIn(24, 30)
        val mask = -1 shl (32 - prefix)
        val network = subnet.address and mask
        val count = 1 shl (32 - prefix)
        return (1 until count - 1)
            .map { network + it }
            .filter { it != subnet.address }
            .map(::toDotted)
    }

    suspend fun probe(
        hosts: List<String>,
        socketFactory: SocketFactory? = null,
        onFound: (host: String, kind: TvKind) -> Unit
    ) = coroutineScope {
        val gate = Semaphore(PARALLELISM)
        for (host in hosts) {
            for ((port, kind) in SERVICE_PORTS) {
                launch(Dispatchers.IO) {
                    gate.withPermit {
                        if (isOpen(host, port, SCAN_TIMEOUT_MS, socketFactory)) onFound(host, kind)
                    }
                }
            }
        }
    }

    fun isOpen(host: String, port: Int, timeoutMs: Int, socketFactory: SocketFactory? = null): Boolean = runCatching {
        (socketFactory?.createSocket() ?: Socket()).use { it.connect(InetSocketAddress(host, port), timeoutMs) }
    }.isSuccess

    fun hostName(host: String): String? = runCatching {
        InetAddress.getByName(host).canonicalHostName.takeIf { it != host }
    }.getOrNull()

    private fun toInt(bytes: ByteArray): Int =
        bytes.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }

    internal fun toDotted(value: Int): String =
        "${value ushr 24 and 0xFF}.${value ushr 16 and 0xFF}.${value ushr 8 and 0xFF}.${value and 0xFF}"

    const val WEBOS_PORT = 3000
    const val WEBOS_SECURE_PORT = 3001
    private const val ADB_PORT = 5555
    private val SERVICE_PORTS = listOf(WEBOS_PORT to TvKind.WebOs, WEBOS_SECURE_PORT to TvKind.WebOs, ADB_PORT to TvKind.Adb)
    private const val PARALLELISM = 96
    private const val SCAN_TIMEOUT_MS = 350
}
