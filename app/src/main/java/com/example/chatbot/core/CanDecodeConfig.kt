package com.example.chatbot.core

/**
 * Human-readable metadata describing how each supported CAN channel is decoded.
 */
data class CanDecodeConfig(
    val canId: String,
    val bytes: String,
    val channel: String,
    val format: String,
    val formula: String,
    val unit: String
)
