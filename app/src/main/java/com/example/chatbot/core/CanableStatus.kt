package com.example.chatbot.core

/**
 * Connection and permission states exposed by [CanableCarDataReader].
 */
enum class CanableStatus {
    DISCONNECTED,
    NO_DEVICE,
    UNSUPPORTED_DEVICE,
    WAITING_FOR_PERMISSION,
    PERMISSION_DENIED,
    CONNECTED,
    SIMULATING,
    ERROR
}
