package com.example.chatbot.core

import android.content.Context
import android.hardware.usb.UsbManager
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
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
        
    private val gson = Gson()
    private var webSocket: WebSocket? = null

    private val _telemetryFlow = MutableSharedFlow<TelemetryPacket>(replay = 1)
    val telemetryFlow: SharedFlow<TelemetryPacket> = _telemetryFlow.asSharedFlow()

    private val _connectionStatus = MutableSharedFlow<String>(replay = 1)
    val connectionStatus: SharedFlow<String> = _connectionStatus.asSharedFlow()

    fun startListening(context: Context, settings: TelemetrySettings = TelemetrySettings()) {
        stopListening()
        when (settings.source) {
            TelemetryInputSource.WEBSOCKET_TESTING -> startWebSocket(settings.webSocketUrl)
            TelemetryInputSource.USB_CAN_REALTIME -> startUsbCan(context, settings.canBitrate)
        }
    }

    private fun startWebSocket(url: String) {
        // We use 10.0.2.2 as the default for Android Emulator accessing host localhost
        val normalizedUrl = normalizeWebSocketUrl(url)
        if (normalizedUrl == null) {
            _connectionStatus.tryEmit("Invalid WebSocket URL. Use ws://host:port/path")
            Log.w("TelemetryManager", "Invalid WebSocket URL: $url")
            return
        }

        val request = try {
            Request.Builder().url(normalizedUrl).build()
        } catch (e: IllegalArgumentException) {
            _connectionStatus.tryEmit("Invalid WebSocket URL: ${e.message ?: "check the address"}")
            Log.e("TelemetryManager", "Invalid WebSocket URL: $url", e)
            return
        }
        
        webSocket = try {
            client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d("TelemetryManager", "WebSocket Connected")
                    _connectionStatus.tryEmit("WebSocket connected")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val packet = gson.fromJson(text, TelemetryPacket::class.java)
                        _telemetryFlow.tryEmit(packet)
                    } catch (e: Exception) {
                        Log.e("TelemetryManager", "Error parsing packet", e)
                        _connectionStatus.tryEmit("Telemetry parse error: ${e.message ?: "invalid packet"}")
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d("TelemetryManager", "WebSocket Closed: $reason")
                    _connectionStatus.tryEmit("WebSocket closed: $reason")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e("TelemetryManager", "WebSocket Failure", t)
                    _connectionStatus.tryEmit("WebSocket failure: ${t.message ?: "unknown error"}")
                    // Implement reconnect logic if necessary
                }
            })
        } catch (e: RuntimeException) {
            _connectionStatus.tryEmit("Could not start WebSocket: ${e.message ?: "unknown error"}")
            Log.e("TelemetryManager", "Could not start WebSocket", e)
            null
        }
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
        webSocket?.close(1000, "User requested stop")
        webSocket = null
        _connectionStatus.tryEmit("Telemetry stopped")
    }
}
