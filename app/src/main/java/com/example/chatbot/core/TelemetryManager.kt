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
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

class TelemetryManager {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
        
    private val gson = Gson()
    private var eventSource: EventSource? = null

    private val _telemetryFlow = MutableSharedFlow<TelemetryPacket>(replay = 1)
    val telemetryFlow: SharedFlow<TelemetryPacket> = _telemetryFlow.asSharedFlow()

    fun startListening(url: String = "https://apexai-812524149286.us-central1.run.app/events/telemetry") {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .build()
        
        val factory = EventSources.createFactory(client)
        eventSource = factory.newEventSource(request, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                Log.d("TelemetryManager", "SSE Connected")
            }

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                try {
                    val packet = gson.fromJson(data, TelemetryPacket::class.java)
                    _telemetryFlow.tryEmit(packet)
                } catch (e: Exception) {
                    Log.e("TelemetryManager", "Error parsing packet", e)
                }
            }

            override fun onClosed(eventSource: EventSource) {
                Log.d("TelemetryManager", "SSE Closed")
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                Log.e("TelemetryManager", "SSE Failure", t)
            }
        })
    }

    fun stopListening() {
        eventSource?.cancel()
        eventSource = null
    }
}
