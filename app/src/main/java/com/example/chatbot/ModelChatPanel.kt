package com.example.kotlin_chatbot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ModelBackendOption(val label: String) {
    CPU("CPU"),
    GPU("GPU"),
    NPU("NPU")
}

data class ModelChatMessage(
    val role: ChatRole,
    val text: String
)

enum class ChatRole {
    User,
    Assistant
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelChatPanel(
    messages: List<ModelChatMessage>,
    input: String,
    selectedBackend: ModelBackendOption,
    loadedModelName: String,
    loadedModelPath: String,
    mtpEnabled: Boolean,
    isGenerating: Boolean,
    firstTokenLatencyMs: Long?,
    totalLatencyMs: Long?,
    errorMessage: String?,
    onInputChange: (String) -> Unit,
    onBackendSelected: (ModelBackendOption) -> Unit,
    onMtpEnabledChange: (Boolean) -> Unit,
    onClearChat: () -> Unit,
    onStopGeneration: () -> Unit,
    onSend: () -> Unit
) {
    val mtpAvailable = selectedBackend != ModelBackendOption.NPU

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Chat",
                modifier = Modifier.weight(1f),
                color = TrackWhite,
                fontWeight = FontWeight.Black,
                fontSize = 24.sp
            )
            IconButton(
                onClick = onClearChat,
                enabled = !isGenerating && messages.isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Clear chat",
                    tint = if (!isGenerating && messages.isNotEmpty()) CheckeredGray else CarbonGray
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModelBackendOption.values().forEach { backend ->
                FilterChip(
                    selected = selectedBackend == backend,
                    onClick = { if (!isGenerating) onBackendSelected(backend) },
                    enabled = !isGenerating,
                    label = { Text(backend.label) }
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Text(
                    text = "First token: ${firstTokenLatencyMs?.let { "${it}ms" } ?: "-"}",
                    color = CheckeredGray,
                    fontSize = 12.sp
                )
                Text(
                    text = "Total: ${totalLatencyMs?.let { "${it}ms" } ?: "-"}",
                    color = CheckeredGray,
                    fontSize = 12.sp
                )
            }
            Text(
                text = "Model: $loadedModelName",
                color = CheckeredGray,
                fontSize = 12.sp
            )
            Text(
                text = loadedModelPath,
                color = CheckeredGray,
                fontSize = 12.sp
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MTP",
                modifier = Modifier.weight(1f),
                color = if (mtpAvailable) TrackWhite else CheckeredGray,
                fontWeight = FontWeight.Bold
            )
            Switch(
                checked = mtpEnabled && mtpAvailable,
                onCheckedChange = onMtpEnabledChange,
                enabled = mtpAvailable && !isGenerating
            )
        }

        ChatTranscript(
            messages = messages,
            isGenerating = isGenerating,
            errorMessage = errorMessage
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = CarbonGray)
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    enabled = !isGenerating,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 56.dp),
                    minLines = 1,
                    maxLines = 4,
                    placeholder = { Text("Message Gemma") }
                )
                if (isGenerating) {
                    IconButton(
                        onClick = onStopGeneration
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = RacingRed
                        )
                    }
                } else {
                    IconButton(
                        onClick = onSend,
                        enabled = input.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (input.isNotBlank()) RacingRed else CheckeredGray
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatTranscript(
    messages: List<ModelChatMessage>,
    isGenerating: Boolean,
    errorMessage: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CarbonGray)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 360.dp)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty()) {
                EmptyChatState()
            }
            messages.forEach { message ->
                ChatBubble(message = message)
            }
            if (isGenerating) {
                GeneratingIndicator()
            }
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = RacingRed,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EmptyChatState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "What can I help with?",
            color = TrackWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Streaming responses will appear here.",
            color = CheckeredGray,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun ChatBubble(message: ModelChatMessage) {
    val isUser = message.role == ChatRole.User

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.86f else 1f),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) RacingRed else AsphaltBlack
            ),
            shape = MaterialTheme.shapes.small
        ) {
            MarkdownText(
                text = message.text.ifBlank { " " },
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun GeneratingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = RacingRed
        )
        Text(
            text = "Generating...",
            color = CheckeredGray,
            fontSize = 12.sp
        )
    }
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val lines = text.split("\n")
    Column(modifier = modifier) {
        var inCodeBlock = false
        val codeBlockContent = StringBuilder()
        
        for (line in lines) {
            if (line.trim().startsWith("```")) {
                if (inCodeBlock) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = AsphaltBlack),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = codeBlockContent.toString().trimEnd(),
                            modifier = Modifier.padding(12.dp),
                            color = TrackWhite,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                    codeBlockContent.setLength(0)
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                continue
            }
            
            if (inCodeBlock) {
                codeBlockContent.append(line).append("\n")
                continue
            }
            
            val trimmedLine = line.trim()
            when {
                trimmedLine.startsWith("# ") -> {
                    Text(
                        text = parseMarkdownToAnnotatedString(trimmedLine.substring(2)),
                        color = TrackWhite,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                trimmedLine.startsWith("## ") -> {
                    Text(
                        text = parseMarkdownToAnnotatedString(trimmedLine.substring(3)),
                        color = TrackWhite,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(top = 3.dp, bottom = 2.dp)
                    )
                }
                trimmedLine.startsWith("### ") -> {
                    Text(
                        text = parseMarkdownToAnnotatedString(trimmedLine.substring(4)),
                        color = TrackWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 1.dp)
                    )
                }
                trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") -> {
                    Row(modifier = Modifier.padding(vertical = 1.dp)) {
                        Text(text = "• ", color = TrackWhite)
                        Text(
                            text = parseMarkdownToAnnotatedString(trimmedLine.substring(2)),
                            color = TrackWhite,
                            lineHeight = 20.sp
                        )
                    }
                }
                trimmedLine.matches(Regex("^\\d+\\.\\s.*")) -> {
                    val dotIndex = trimmedLine.indexOf('.')
                    val num = trimmedLine.substring(0, dotIndex + 1)
                    val content = trimmedLine.substring(dotIndex + 1).trim()
                    Row(modifier = Modifier.padding(vertical = 1.dp)) {
                        Text(text = "$num ", color = TrackWhite)
                        Text(
                            text = parseMarkdownToAnnotatedString(content),
                            color = TrackWhite,
                            lineHeight = 20.sp
                        )
                    }
                }
                else -> {
                    if (trimmedLine.isNotEmpty()) {
                        Text(
                            text = parseMarkdownToAnnotatedString(line),
                            color = TrackWhite,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
        
        if (inCodeBlock && codeBlockContent.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                colors = CardDefaults.cardColors(containerColor = AsphaltBlack),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = codeBlockContent.toString().trimEnd(),
                    modifier = Modifier.padding(12.dp),
                    color = TrackWhite,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }
    }
}

fun parseMarkdownToAnnotatedString(text: String): AnnotatedString {
    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            // Bold (** or __)
            if (index + 1 < text.length && (text.startsWith("**", index) || text.startsWith("__", index))) {
                val token = text.substring(index, index + 2)
                val end = text.indexOf(token, index + 2)
                if (end != -1) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(text.substring(index + 2, end))
                    pop()
                    index = end + 2
                    continue
                }
            }
            // Italic (* or _)
            if (text[index] == '*' || text[index] == '_') {
                val token = text[index].toString()
                val end = text.indexOf(token, index + 1)
                if (end != -1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(text.substring(index + 1, end))
                    pop()
                    index = end + 1
                    continue
                }
            }
            // Inline code (`)
            if (text[index] == '`') {
                val end = text.indexOf('`', index + 1)
                if (end != -1) {
                    pushStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = CheckeredGray.copy(alpha = 0.2f),
                        color = RacingRed
                    ))
                    append(text.substring(index + 1, end))
                    pop()
                    index = end + 1
                    continue
                }
            }
            append(text[index])
            index++
        }
    }
}
