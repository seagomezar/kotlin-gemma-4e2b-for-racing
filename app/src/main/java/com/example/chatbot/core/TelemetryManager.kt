package com.example.chatbot.core

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
import java.util.concurrent.TimeUnit

class TelemetryManager {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
        
    private val gson = Gson()
    private var webSocket: WebSocket? = null

    private val _telemetryFlow = MutableSharedFlow<TelemetryPacket>(replay = 1)
    val telemetryFlow: SharedFlow<TelemetryPacket> = _telemetryFlow.asSharedFlow()

    fun startListening(url: String = "ws://10.0.2.2:8000/ws/telemetry") {
        // We use 10.0.2.2 as the default for Android Emulator accessing host localhost
        val request = Request.Builder().url(url).build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("TelemetryManager", "WebSocket Connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val packet = gson.fromJson(text, TelemetryPacket::class.java)
                    _telemetryFlow.tryEmit(packet)
                } catch (e: Exception) {
                    Log.e("TelemetryManager", "Error parsing packet", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("TelemetryManager", "WebSocket Closed: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("TelemetryManager", "WebSocket Failure", t)
                // Implement reconnect logic if necessary
            }
        })
    }

    fun stopListening() {
        webSocket?.close(1000, "User requested stop")
        webSocket = null
    }
}
