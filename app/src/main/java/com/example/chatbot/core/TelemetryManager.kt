package com.example.chatbot.core

import android.content.Context
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.chatbot.models.TelemetryPacket
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import android.os.SystemClock
import java.util.concurrent.TimeUnit

enum class TelemetryInputSource {
    WEBSOCKET_TESTING,
    USB_CAN_REALTIME
}

data class TelemetrySettings(
    val source: TelemetryInputSource = TelemetryInputSource.WEBSOCKET_TESTING,
    val webSocketUrl: String = "ws://10.0.2.2:8000/ws/telemetry",
    val canBitrate: Int = 500_000
)

class TelemetryManager {
    private companion object {
        const val STALE_TELEMETRY_TIMEOUT_MS = 8_000L
        const val STALE_TELEMETRY_CHECK_MS = 2_000L
        const val RECONNECT_DELAY_MS = 2_000L
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()
        
    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private val staleTelemetryHandler = Handler(Looper.getMainLooper())
    private var shouldReconnect = false
    private var reconnectUrl: String? = null
    private var lastTelemetryAtMs = 0L
    private val staleTelemetryCheck = object : Runnable {
        override fun run() {
            if (!shouldReconnect) {
                return
            }

            val lastPacketAgeMs = SystemClock.elapsedRealtime() - lastTelemetryAtMs
            if (lastTelemetryAtMs > 0 && lastPacketAgeMs > STALE_TELEMETRY_TIMEOUT_MS) {
                _connectionStatus.tryEmit("Telemetry stale, reconnecting...")
                Log.w("TelemetryManager", "Telemetry stale for ${lastPacketAgeMs}ms; reconnecting")
                webSocket?.cancel()
                scheduleReconnect()
                return
            }

            staleTelemetryHandler.postDelayed(this, STALE_TELEMETRY_CHECK_MS)
        }
    }

    private val _telemetryFlow = MutableSharedFlow<TelemetryPacket>(replay = 1)
    val telemetryFlow: SharedFlow<TelemetryPacket> = _telemetryFlow.asSharedFlow()

    private val _connectionStatus = MutableSharedFlow<String>(replay = 1)
    val connectionStatus: SharedFlow<String> = _connectionStatus.asSharedFlow()

    fun startListening(context: Context, settings: TelemetrySettings = TelemetrySettings()) {
        stopListening()
        when (settings.source) {
            TelemetryInputSource.WEBSOCKET_TESTING -> {
                shouldReconnect = true
                startWebSocket(settings.webSocketUrl)
            }
            TelemetryInputSource.USB_CAN_REALTIME -> startUsbCan(context, settings.canBitrate)
        }
    }

    private fun startWebSocket(url: String) {
        // We use 10.0.2.2 as the default for Android Emulator accessing host localhost
        val normalizedUrl = normalizeWebSocketUrl(url)
        if (normalizedUrl == null) {
            shouldReconnect = false
            _connectionStatus.tryEmit("Invalid WebSocket URL. Use ws://host:port/path")
            Log.w("TelemetryManager", "Invalid WebSocket URL: $url")
            return
        }
        reconnectUrl = normalizedUrl
        lastTelemetryAtMs = SystemClock.elapsedRealtime()

        val request = try {
            Request.Builder().url(normalizedUrl).build()
        } catch (e: IllegalArgumentException) {
            shouldReconnect = false
            _connectionStatus.tryEmit("Invalid WebSocket URL: ${e.message ?: "check the address"}")
            Log.e("TelemetryManager", "Invalid WebSocket URL: $url", e)
            return
        }
        
        webSocket = try {
            client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d("TelemetryManager", "WebSocket Connected")
                    lastTelemetryAtMs = SystemClock.elapsedRealtime()
                    staleTelemetryHandler.removeCallbacks(staleTelemetryCheck)
                    staleTelemetryHandler.postDelayed(staleTelemetryCheck, STALE_TELEMETRY_CHECK_MS)
                    _connectionStatus.tryEmit("WebSocket connected")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val packet = gson.fromJson(text, TelemetryPacket::class.java)
                        lastTelemetryAtMs = SystemClock.elapsedRealtime()
                        _telemetryFlow.tryEmit(packet)
                    } catch (e: Exception) {
                        Log.e("TelemetryManager", "Error parsing packet", e)
                        _connectionStatus.tryEmit("Telemetry parse error: ${e.message ?: "invalid packet"}")
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d("TelemetryManager", "WebSocket Closed: $reason")
                    _connectionStatus.tryEmit("WebSocket closed: $reason")
                    scheduleReconnect()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e("TelemetryManager", "WebSocket Failure", t)
                    _connectionStatus.tryEmit("WebSocket failure: ${t.message ?: "unknown error"}")
                    scheduleReconnect()
                }
            })
        } catch (e: RuntimeException) {
            _connectionStatus.tryEmit("Could not start WebSocket: ${e.message ?: "unknown error"}")
            Log.e("TelemetryManager", "Could not start WebSocket", e)
            null
        }
    }

    private fun scheduleReconnect() {
        val url = reconnectUrl
        if (!shouldReconnect || url == null) {
            return
        }

        webSocket = null
        reconnectHandler.removeCallbacksAndMessages(null)
        staleTelemetryHandler.removeCallbacks(staleTelemetryCheck)
        _connectionStatus.tryEmit("WebSocket reconnecting...")
        reconnectHandler.postDelayed(
            {
                if (shouldReconnect) {
                    startWebSocket(url)
                }
            },
            2_000
        )
    }

    private fun normalizeWebSocketUrl(url: String): String? {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) {
            return null
        }

        val validationUrl = when {
            trimmedUrl.startsWith("ws://") -> "http://${trimmedUrl.removePrefix("ws://")}"
            trimmedUrl.startsWith("wss://") -> "https://${trimmedUrl.removePrefix("wss://")}"
            else -> return null
        }.toHttpUrlOrNull() ?: return null

        val scheme = if (validationUrl.isHttps) "wss://" else "ws://"
        val encodedPath = validationUrl.encodedPath
        val encodedQuery = validationUrl.encodedQuery?.let { "?$it" }.orEmpty()
        val encodedFragment = validationUrl.encodedFragment?.let { "#$it" }.orEmpty()
        return "$scheme${validationUrl.host}:${validationUrl.port}$encodedPath$encodedQuery$encodedFragment"
    }

    private fun startUsbCan(context: Context, bitrate: Int) {
        shouldReconnect = false
        reconnectUrl = null
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val devices = usbManager.deviceList.values.toList()

        if (devices.isEmpty()) {
            _connectionStatus.tryEmit("No USB-C CAN adapter detected")
            Log.w("TelemetryManager", "USB-C CAN selected, but no USB devices are attached")
            return
        }

        val deviceSummary = devices.joinToString { device ->
            "vendor=${device.vendorId}, product=${device.productId}"
        }
        _connectionStatus.tryEmit(
            "USB-C CAN adapter detected at ${bitrate} bps. Decoder setup required for: $deviceSummary"
        )
        Log.i("TelemetryManager", "USB-C CAN devices detected: $deviceSummary")

        // Adapter protocols are vendor-specific. The next implementation step is
        // to request USB permission, open the selected UsbDevice endpoint, parse
        // its CAN frame format, and emit TelemetryPacket objects into
        // _telemetryFlow just like the WebSocket path does.
    }

    fun stopListening() {
        shouldReconnect = false
        reconnectUrl = null
        reconnectHandler.removeCallbacksAndMessages(null)
        staleTelemetryHandler.removeCallbacks(staleTelemetryCheck)
        webSocket?.close(1000, "User requested stop")
        webSocket = null
        _connectionStatus.tryEmit("Telemetry stopped")
    }
}
