package com.example.chatbot.core

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalApi::class)
class Gemma4Lite(private val context: Context) {
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var activeModelPath: String? = null
    private var activeBackendName: String? = null
    private var activeSystemPrompt: String? = null
    private val gson = Gson()

    suspend fun initialize(
        modelPath: String = DEFAULT_MODEL_PATH,
        backend: Backend = Backend.CPU(),
        enableMtp: Boolean = false
    ) {
        ExperimentalFlags.enableSpeculativeDecoding = enableMtp

        val backendName = backend.toString()
        if (
            engine != null &&
            activeModelPath == modelPath &&
            activeBackendName == backendName
        ) {
            return
        }

        close()

        val config = EngineConfig(
            modelPath = modelPath,
            backend = backend,
            cacheDir = context.cacheDir.absolutePath
        )
        engine = Engine(config).also { it.initialize() }
        activeModelPath = modelPath
        activeBackendName = backendName
        activeSystemPrompt = null
    }

    fun generateStreaming(
        prompt: String,
        systemPrompt: String? = null
    ): Flow<String> {
        val currentConversation = conversationFor(systemPrompt)
        return currentConversation
            .sendMessageAsync(prompt)
            .map { message -> message.textContent() }
    }

    fun <T : Any> generateJSON(
        prompt: String,
        responseClass: Class<T>,
        systemPrompt: String? = null
    ): T {
        val currentConversation = conversationFor(systemPrompt)
        val responseText = currentConversation
            .sendMessage(prompt)
            .textContent()
            .trim()
            .stripJsonMarkdown()

        return gson.fromJson(responseText, responseClass)
    }

    fun close() {
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
        activeModelPath = null
        activeBackendName = null
        activeSystemPrompt = null
    }

    private fun conversationFor(systemPrompt: String?): Conversation {
        val currentEngine = engine ?: error("Gemma engine not initialized")
        if (conversation != null && activeSystemPrompt == systemPrompt) {
            return conversation!!
        }

        conversation?.close()
        conversation = currentEngine.createConversation(
            ConversationConfig(
                systemInstruction = systemPrompt?.let { Contents.of(it) } ?: Contents.Companion.of("")
            )
        )
        activeSystemPrompt = systemPrompt
        return conversation!!
    }

    private fun Message.textContent(): String {
        return contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString(separator = "") { it.text }
            .ifBlank { toString() }
    }

    private fun String.stripJsonMarkdown(): String {
        return removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private companion object {
        private const val DEFAULT_MODEL_PATH = "/data/local/tmp/llm/gemma-4-E2B-it.litertlm"
    }
}
