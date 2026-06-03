package com.example.chatbot.core

/**
 * Parsed standard 11-bit SLCAN frame with its CAN ID, data length code, and
 * payload bytes.
 */
data class SlcanFrame(
    val canId: Int,
    val dlc: Int,
    val data: ByteArray
) {
    init {
        require(dlc == data.size)
    }
}
