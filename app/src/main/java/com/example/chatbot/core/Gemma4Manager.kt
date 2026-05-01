package com.example.chatbot.core

import android.content.Context
import com.example.chatbot.models.CoachingPayload
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.gson.Gson

class Gemma4Manager(private val context: Context) {
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private val gson = Gson()

    suspend fun initialize() {
        if (conversation != null) return

        if (engine == null) {
            val config = EngineConfig(
                modelPath = "/data/local/tmp/llm/gemma-4-E2B-it.litertlm",
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.absolutePath
            )

            val newEngine = Engine(config)
            newEngine.initialize()
            engine = newEngine
        }

        if (conversation == null) {
            conversation = engine!!.createConversation()
            // Set the strict system prompt for Hyper-Specific Micro-Adjustments and JSON format
            val systemPrompt = """
                You are T-Rod's AI race engineer. Your goal is to provide hyper-specific micro-adjustments for racing corners.
                You will receive telemetry data. You must respond ONLY with a JSON object matching this schema:
                {
                    "instruction": "e.g., Turn in 100 feet earlier",
                    "urgency": "NORMAL or HIGH",
                    "targetCorner": "e.g., Turn 3"
                }
                Do not include markdown blocks, just the JSON string.
            """.trimIndent()
            conversation?.sendMessage(systemPrompt)
        }
    }

    fun generateCoaching(telemetryJson: String): CoachingPayload {
        val currentConversation = conversation ?: error("Conversation not initialized")
        val responseText = currentConversation.sendMessage(telemetryJson).toString()
        
        return try {
            gson.fromJson(responseText.trim().removePrefix("```json").removeSuffix("```").trim(), CoachingPayload::class.java)
        } catch (e: Exception) {
            // Fallback payload if LLM fails to format JSON correctly
            CoachingPayload(
                instruction = "Keep pushing!",
                urgency = "NORMAL",
                targetCorner = "Unknown"
            )
        }
    }

    fun close() {
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
    }
}
