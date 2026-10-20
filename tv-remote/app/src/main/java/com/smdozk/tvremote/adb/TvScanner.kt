package com.smdozk.tvremote.adb

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

object TvScanner {

    data class Subnet(val address: Int, val prefixLength: Int)

    fun wifiSubnet(context: Context): Subnet? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return null
        // Not activeNetwork: with a VPN on, that is the tunnel rather than the LAN the TV is on.
        @Suppress("DEPRECATION")
        val link = connectivity.allNetworks
            .filter { network ->
                val capabilities = connectivity.getNetworkCapabilities(network)
                capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                    )
            }
            .firstNotNullOfOrNull { network ->
                connectivity.getLinkProperties(network)?.linkAddresses?.firstOrNull { it.address is Inet4Address }
            } ?: return null
        return Subnet(toInt(link.address.address), link.prefixLength)
    }

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
        port: Int,
        onFound: (host: String) -> Unit
    ) = coroutineScope {
        val gate = Semaphore(PARALLELISM)
        hosts.forEach { host ->
            launch(Dispatchers.IO) {
                gate.withPermit {
                    val open = runCatching {
                        Socket().use { it.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS) }
                    }.isSuccess
                    if (open) onFound(host)
                }
            }
        }
    }

    fun hostName(host: String): String? = runCatching {
        InetAddress.getByName(host).canonicalHostName.takeIf { it != host }
    }.getOrNull()

    private fun toInt(bytes: ByteArray): Int =
        bytes.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }

    internal fun toDotted(value: Int): String =
        "${value ushr 24 and 0xFF}.${value ushr 16 and 0xFF}.${value ushr 8 and 0xFF}.${value and 0xFF}"

    private const val PARALLELISM = 64
    private const val CONNECT_TIMEOUT_MS = 400
}
