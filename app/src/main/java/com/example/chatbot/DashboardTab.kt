package com.example.kotlin_chatbot

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.ui.graphics.vector.ImageVector

enum class DashboardTab(
    val label: String,
    val icon: ImageVector
) {
    AiCoach("AI Coach", Icons.Default.SmartToy),
    Chat("Chat", Icons.AutoMirrored.Filled.Chat),
    MemoryBank("Memory Bank", Icons.Default.Memory),
    DataLog("Data Log", Icons.Default.DirectionsCar);

    companion object {
        fun fromIndex(index: Int): DashboardTab = values().getOrElse(index) { AiCoach }
    }
}
