package com.smdozk.tvremote.webos

import com.smdozk.tvremote.adb.TvKeyCode
import com.smdozk.tvremote.model.TvInput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

// The TV answered with an error; the connection itself is fine.
class WebOsException(message: String) : IOException(message)

class WebOsPairingException(message: String) : IOException(message)

data class WebOsApp(val id: String, val title: String, val removable: Boolean)

// LG webOS "SSAP" second-screen protocol: JSON requests over a websocket, buttons over a second socket.
class WebOsClient(private val host: String, private val onClosed: (Throwable) -> Unit) {

    private val http = buildClient()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private val ids = AtomicInteger()
    private val pointerLock = Mutex()

    @Volatile private var main: WebSocket? = null
    @Volatile private var pointer: WebSocket? = null
    @Volatile private var registration: CompletableDeferred<String>? = null
    @Volatile private var onPrompt: (() -> Unit)? = null
    @Volatile private var closing = false
    private var playing = true

    var systemInfo: JSONObject? = null
        private set

    suspend fun connect(clientKey: String?, onPairingPrompt: () -> Unit): String {
        // Older firmware speaks plain ws on 3000; newer firmware only accepts wss on 3001.
        val socket = open("ws://$host:$WS_PORT") ?: open("wss://$host:$WSS_PORT")
            ?: throw ConnectException("webOS TV'ye bağlanılamadı ($host)")
        main = socket

        exchangeHello()
        // Newer webOS refuses some calls unless system info was read before registering.
        systemInfo = runCatching { request("system/getSystemInfo") }.getOrNull()

        val registered = CompletableDeferred<String>()
        registration = registered
        onPrompt = onPairingPrompt
        val message = JSONObject(REGISTRATION).put("id", REGISTER_ID)
        message.getJSONObject("payload").put("client-key", clientKey ?: JSONObject.NULL)
        if (!socket.send(message.toString())) throw IOException("Bağlantı kapandı")

        val key = withTimeoutOrNull(PAIRING_TIMEOUT_MS) { registered.await() }
            ?: throw WebOsPairingException("TV'deki bağlantı isteği onaylanmadı")
        if (key.isBlank()) throw WebOsPairingException("TV istemci anahtarı vermedi")
        return key
    }

    suspend fun request(uri: String, payload: JSONObject = JSONObject()): JSONObject {
        val socket = main ?: throw IOException("TV'ye bağlı değil")
        val id = "req_${ids.incrementAndGet()}"
        val response = CompletableDeferred<JSONObject>()
        pending[id] = response
        val message = JSONObject()
            .put("id", id)
            .put("type", "request")
            .put("uri", "ssap://$uri")
            .put("payload", payload)
        try {
            if (!socket.send(message.toString())) throw IOException("Bağlantı kapandı")
            val body = withTimeout(REQUEST_TIMEOUT_MS) { response.await() }.optJSONObject("payload") ?: JSONObject()
            if (body.has("returnValue") && !body.optBoolean("returnValue")) {
                throw WebOsException(body.optString("errorText", "TV isteği reddetti"))
            }
            return body
        } finally {
            pending.remove(id)
        }
    }

    suspend fun button(name: String) {
        val message = "type:button\nname:$name\n\n"
        if (!pointerSocket().send(message)) {
            pointer = null
            if (!pointerSocket().send(message)) throw IOException("Kumanda kanalı kapandı")
        }
    }

    suspend fun sendKey(keyCode: Int) {
        when (keyCode) {
            TvKeyCode.POWER -> request("system/turnOff")
            TvKeyCode.DEL -> request("com.webos.service.ime/deleteCharacters", JSONObject().put("count", 1))
            // webOS has separate play and pause buttons, so the toggle is tracked here.
            TvKeyCode.MEDIA_PLAY_PAUSE -> {
                button(if (playing) "PAUSE" else "PLAY")
                playing = !playing
            }
            else -> button(BUTTONS[keyCode] ?: return)
        }
    }

    suspend fun insertText(text: String) {
        request("com.webos.service.ime/insertText", JSONObject().put("text", text).put("replace", 0))
    }

    suspend fun launchPoints(): List<WebOsApp> {
        val points = request("com.webos.applicationManager/listLaunchPoints").optJSONArray("launchPoints") ?: JSONArray()
        return (0 until points.length()).mapNotNull { index ->
            val point = points.optJSONObject(index) ?: return@mapNotNull null
            val id = point.optString("id").ifBlank { return@mapNotNull null }
            WebOsApp(id, point.optString("title").ifBlank { id }, point.optBoolean("removable"))
        }
    }

    suspend fun launch(appId: String) {
        request("system.launcher/launch", JSONObject().put("id", appId))
    }

    suspend fun closeApp(appId: String) {
        request("system.launcher/close", JSONObject().put("id", appId))
    }

    suspend fun inputs(): List<TvInput> {
        val devices = request("tv/getExternalInputList").optJSONArray("devices") ?: JSONArray()
        return (0 until devices.length()).mapNotNull { index ->
            val device = devices.optJSONObject(index) ?: return@mapNotNull null
            val id = device.optString("id").ifBlank { return@mapNotNull null }
            TvInput(id, device.optString("label").ifBlank { id })
        }
    }

    suspend fun switchInput(inputId: String) {
        request("tv/switchInput", JSONObject().put("inputId", inputId))
    }

    suspend fun softwareInfo(): JSONObject = request("com.webos.service.update/getCurrentSWInformation")

    fun close() {
        closing = true
        pointer?.close(NORMAL_CLOSURE, null)
        main?.close(NORMAL_CLOSURE, null)
        pointer = null
        main = null
        failPending(IOException("Bağlantı kapatıldı"))
    }

    private suspend fun exchangeHello() {
        val hello = CompletableDeferred<JSONObject>()
        pending[HELLO_ID] = hello
        main?.send(JSONObject().put("id", HELLO_ID).put("type", "hello").put("payload", JSONObject()).toString())
        withTimeoutOrNull(HELLO_TIMEOUT_MS) { hello.await() }
        pending.remove(HELLO_ID)
    }

    private suspend fun open(url: String): WebSocket? {
        val opened = CompletableDeferred<WebSocket?>()
        http.newWebSocket(Request.Builder().url(url).build(), MainListener(opened))
        return withTimeoutOrNull(OPEN_TIMEOUT_MS) { opened.await() }
    }

    private suspend fun pointerSocket(): WebSocket = pointerLock.withLock {
        pointer?.let { return@withLock it }
        val path = request("com.webos.service.networkinput/getPointerInputSocket").optString("socketPath")
        if (path.isBlank()) throw WebOsException("TV kumanda kanalı vermedi")
        val opened = CompletableDeferred<WebSocket?>()
        http.newWebSocket(Request.Builder().url(path).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.complete(webSocket)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                opened.complete(null)
                if (pointer === webSocket) pointer = null
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (pointer === webSocket) pointer = null
                webSocket.close(NORMAL_CLOSURE, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (pointer === webSocket) pointer = null
            }
        })
        val socket = withTimeoutOrNull(OPEN_TIMEOUT_MS) { opened.await() } ?: throw IOException("Kumanda kanalı açılamadı")
        pointer = socket
        socket
    }

    private fun handle(text: String) {
        val message = runCatching { JSONObject(text) }.getOrNull() ?: return
        val id = message.opt("id")?.toString()
        val type = message.optString("type")
        if (id == REGISTER_ID) {
            val payload = message.optJSONObject("payload")
            when (type) {
                "registered" -> registration?.complete(payload?.optString("client-key").orEmpty())
                "error" -> registration?.completeExceptionally(
                    WebOsPairingException(message.optString("error").ifBlank { "Eşleştirme reddedildi" })
                )
                "response" -> if (payload?.optString("pairingType") == "PROMPT") onPrompt?.invoke()
            }
            return
        }
        val waiting = (if (type == "hello") pending[HELLO_ID] else id?.let(pending::get)) ?: return
        if (type == "error") {
            waiting.completeExceptionally(WebOsException(message.optString("error").ifBlank { "TV hata döndürdü" }))
        } else {
            waiting.complete(message)
        }
    }

    private fun failPending(error: Throwable) {
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
        registration?.completeExceptionally(error)
    }

    private inner class MainListener(private val opened: CompletableDeferred<WebSocket?>) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            opened.complete(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) = handle(text)

        // OkHttp reports onClosed only after we answer the TV's close frame.
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(NORMAL_CLOSURE, null)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            // A failed open just means "try the other port".
            if (opened.complete(null)) return
            if (main !== webSocket) return
            main = null
            failPending(t)
            if (!closing) onClosed(t)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (main !== webSocket) return
            main = null
            val error = IOException("TV bağlantıyı kapattı")
            failPending(error)
            if (!closing) onClosed(error)
        }
    }

    private companion object {
        const val WS_PORT = 3000
        const val WSS_PORT = 3001
        const val HELLO_ID = "hello"
        const val REGISTER_ID = "register_0"
        const val NORMAL_CLOSURE = 1000
        const val OPEN_TIMEOUT_MS = 4_000L
        const val HELLO_TIMEOUT_MS = 3_000L
        const val REQUEST_TIMEOUT_MS = 15_000L
        const val PAIRING_TIMEOUT_MS = 60_000L

        val BUTTONS = mapOf(
            TvKeyCode.DPAD_UP to "UP",
            TvKeyCode.DPAD_DOWN to "DOWN",
            TvKeyCode.DPAD_LEFT to "LEFT",
            TvKeyCode.DPAD_RIGHT to "RIGHT",
            TvKeyCode.DPAD_CENTER to "ENTER",
            TvKeyCode.BACK to "BACK",
            TvKeyCode.HOME to "HOME",
            TvKeyCode.MENU to "MENU",
            TvKeyCode.SETTINGS to "MENU",
            TvKeyCode.TV_INPUT to "INPUT_HUB",
            TvKeyCode.VOLUME_UP to "VOLUMEUP",
            TvKeyCode.VOLUME_DOWN to "VOLUMEDOWN",
            TvKeyCode.VOLUME_MUTE to "MUTE",
            TvKeyCode.CHANNEL_UP to "CHANNELUP",
            TvKeyCode.CHANNEL_DOWN to "CHANNELDOWN",
            TvKeyCode.MEDIA_PLAY to "PLAY",
            TvKeyCode.MEDIA_PAUSE to "PAUSE",
            TvKeyCode.MEDIA_STOP to "STOP",
            TvKeyCode.MEDIA_REWIND to "REWIND",
            TvKeyCode.MEDIA_FAST_FORWARD to "FASTFORWARD",
            TvKeyCode.MEDIA_PREVIOUS to "REWIND",
            TvKeyCode.MEDIA_NEXT to "FASTFORWARD",
            TvKeyCode.NUM_0 to "0",
            TvKeyCode.NUM_1 to "1",
            TvKeyCode.NUM_2 to "2",
            TvKeyCode.NUM_3 to "3",
            TvKeyCode.NUM_4 to "4",
            TvKeyCode.NUM_5 to "5",
            TvKeyCode.NUM_6 to "6",
            TvKeyCode.NUM_7 to "7",
            TvKeyCode.NUM_8 to "8",
            TvKeyCode.NUM_9 to "9",
        )

        // Same payload Home Assistant's aiowebostv sends; the TV shows its own "allow" prompt for it.
        val REGISTRATION = """
            {"type":"register","payload":{"forcePairing":false,"pairingType":"PROMPT","manifest":{
            "appVersion":"1.1","manifestVersion":1,"permissions":[
            "APP_TO_APP","CLOSE","CONTROL_AUDIO","CONTROL_DISPLAY","CONTROL_INPUT_JOYSTICK",
            "CONTROL_INPUT_MEDIA_PLAYBACK","CONTROL_INPUT_MEDIA_RECORDING","CONTROL_INPUT_TEXT",
            "CONTROL_INPUT_TV","CONTROL_MOUSE_AND_KEYBOARD","CONTROL_POWER","CONTROL_TV_SCREEN",
            "LAUNCH","LAUNCH_WEBAPP","READ_APP_STATUS","READ_COUNTRY_INFO","READ_CURRENT_CHANNEL",
            "READ_INPUT_DEVICE_LIST","READ_INSTALLED_APPS","READ_LGE_SDX","READ_LGE_TV_INPUT_EVENTS",
            "READ_NETWORK_STATE","READ_NOTIFICATIONS","READ_POWER_STATE","READ_RUNNING_APPS",
            "READ_SETTINGS","READ_TV_CHANNEL_LIST","READ_TV_CURRENT_TIME","READ_UPDATE_INFO","SEARCH",
            "TEST_OPEN","TEST_PROTECTED","TEST_SECURE","UPDATE_FROM_REMOTE_APP",
            "WRITE_NOTIFICATION_ALERT","WRITE_NOTIFICATION_TOAST","WRITE_SETTINGS"]}}}
        """.trimIndent()

        // webOS serves its websocket with a self-signed certificate; this client only talks to the chosen TV.
        fun buildClient(): OkHttpClient {
            val trustAll = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }
            return OkHttpClient.Builder()
                // Older TVs may only offer legacy TLS on 3001; OkHttp retries with the next spec.
                .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
                .sslSocketFactory(ssl.socketFactory, trustAll)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(OPEN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build()
        }
    }
}
