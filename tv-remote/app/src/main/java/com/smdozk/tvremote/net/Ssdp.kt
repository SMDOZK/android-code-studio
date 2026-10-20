package com.smdozk.tvremote.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLDecoder
import kotlin.coroutines.coroutineContext

// UPnP/SSDP discovery: webOS TVs answer an M-SEARCH with their name and a stable uuid.
object Ssdp {

    data class Reply(val host: String, val headers: Map<String, String>) {
        val server: String get() = headers["server"].orEmpty()
        val location: String? get() = headers["location"]
        val uuid: String? get() = headers["usn"]?.substringAfter("uuid:", "")?.substringBefore("::")?.trim()?.ifBlank { null }

        val isWebOs: Boolean
            get() = server.contains("webos", ignoreCase = true) ||
                headers["st"].orEmpty().contains("lge-com", ignoreCase = true) ||
                headers.containsKey(LG_NAME_HEADER)

        // LG sends the TV's friendly name URL-encoded in its own header.
        val friendlyName: String?
            get() = headers[LG_NAME_HEADER]
                ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
                ?.trim()?.ifBlank { null }
    }

    val MULTICAST = InetSocketAddress("239.255.255.250", 1900)

    private const val LG_NAME_HEADER = "dlnadevicename.lge.com"
    private val SEARCH_TARGETS = listOf(
        "urn:lge-com:service:webos-second-screen:1",
        "urn:dial-multiscreen-org:service:dial:1",
        "urn:schemas-upnp-org:device:MediaRenderer:1",
    )
    // UDP gets lost on busy Wi-Fi: repeat the search a few times within the window.
    private val SEND_AT_MS = listOf(0L, 700L, 1800L)

    fun searchMessage(target: String): String =
        "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1900\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 2\r\n" +
            "ST: $target\r\n" +
            "USER-AGENT: Android UPnP/1.1 TVRemote/1.3\r\n\r\n"

    fun parse(host: String, data: String): Reply? {
        val lines = data.split("\r\n", "\n")
        val status = lines.firstOrNull()?.trim().orEmpty()
        // Search replies are "HTTP/1.1 200 OK"; NOTIFY announcements are fine too.
        if (!status.startsWith("HTTP/1.1 200") && !status.startsWith("NOTIFY")) return null
        val headers = lines.drop(1).mapNotNull { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) null else line.substring(0, colon).trim().lowercase() to line.substring(colon + 1).trim()
        }.toMap()
        return Reply(host, headers)
    }

    /**
     * Sends M-SEARCH and reports each distinct responder once.
     * [configure] lets the caller bind the socket to the Wi-Fi network before anything is sent.
     */
    suspend fun search(
        windowMs: Long = 3_000,
        target: InetSocketAddress = MULTICAST,
        configure: (DatagramSocket) -> Unit = {},
        onReply: (Reply) -> Unit
    ) = withContext(Dispatchers.IO) {
        DatagramSocket(null).use { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(0))
            configure(socket)
            socket.soTimeout = 200
            val seen = HashSet<String>()
            val buffer = ByteArray(8192)
            val start = System.nanoTime()
            var sent = 0
            while (true) {
                coroutineContext.ensureActive()
                val elapsed = (System.nanoTime() - start) / 1_000_000
                if (elapsed >= windowMs) break
                while (sent < SEND_AT_MS.size && elapsed >= SEND_AT_MS[sent]) {
                    for (st in SEARCH_TARGETS) {
                        val bytes = searchMessage(st).toByteArray()
                        runCatching { socket.send(DatagramPacket(bytes, bytes.size, target)) }
                    }
                    sent++
                }
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (e: SocketTimeoutException) {
                    continue
                }
                val host = packet.address?.hostAddress ?: continue
                val reply = parse(host, String(packet.data, 0, packet.length, Charsets.UTF_8)) ?: continue
                // One TV answers every search target; only replies that add something are reported.
                val key = "$host|${reply.isWebOs}|${reply.friendlyName}|${reply.uuid}"
                if (seen.add(key)) onReply(reply)
            }
        }
    }

    /** Reads friendlyName/modelName from the UPnP device description at [location]. */
    fun describe(location: String, timeoutMs: Int = 1_500): Pair<String?, String?> = runCatching {
        val connection = URL(location).openConnection() as HttpURLConnection
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        try {
            val xml = connection.inputStream.bufferedReader().use { it.readText().take(64_000) }
            xmlTag(xml, "friendlyName") to xmlTag(xml, "modelName")
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(null to null)

    internal fun xmlTag(xml: String, tag: String): String? =
        Regex("<$tag>\\s*([^<]*?)\\s*</$tag>").find(xml)?.groupValues?.get(1)
            ?.replace("&amp;", "&")?.replace("&lt;", "<")?.replace("&gt;", ">")?.replace("&quot;", "\"")?.replace("&apos;", "'")
            ?.ifBlank { null }
}
