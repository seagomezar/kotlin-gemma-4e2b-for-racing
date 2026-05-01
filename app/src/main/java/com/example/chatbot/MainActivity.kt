package com.example.kotlin_chatbot

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Send
import androidx.compose.ui.text.font.FontStyle
import com.google.ai.edge.litertlm.*
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults

// ---------- THEME ----------

private val RacingRed = Color(0xFFD50000)
private val AsphaltBlack = Color(0xFF121212)
private val CarbonGray = Color(0xFF242424)
private val TrackWhite = Color(0xFFFFFFFF)
private val CheckeredGray = Color(0xFF9E9E9E)

private val RacingColorScheme = darkColorScheme(
    primary = RacingRed,
    onPrimary = TrackWhite,
    secondary = TrackWhite,
    onSecondary = AsphaltBlack,
    surface = AsphaltBlack,
    onSurface = TrackWhite,
    background = AsphaltBlack,
    onBackground = TrackWhite,
    surfaceVariant = CarbonGray,
    onSurfaceVariant = TrackWhite
)

private val RacingShapes = Shapes(
    small = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
    medium = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp),
    large = CutCornerShape(topStart = 24.dp, bottomEnd = 24.dp)
)

// ---------- DATA ----------

data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

// ---------- ACTIVITY ----------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = RacingColorScheme, shapes = RacingShapes) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatScreen()
                }
            }
        }
    }
}

// ---------- GEMMA MANAGER ----------

class Gemma4Manager(private val context: Context) {
    private var engine: Engine? = null
    private var conversation: Conversation? = null

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
        }
    }

    fun reply(prompt: String): String {
        val currentConversation = conversation ?: error("Conversation not initialized")
        return currentConversation.sendMessage(prompt).toString()
    }

    fun close() {
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
    }
}

// ---------- UI ----------


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val messages = remember {
        mutableStateListOf(
            ChatMessage("Hello! I am your on-device Gemma 4:E2B chatbot.", false)
        )
    }

    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val gemmaManager = remember { Gemma4Manager(context) }

    DisposableEffect(Unit) {
        onDispose { gemmaManager.close() }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ---------- HEADER (STICKY) ----------
        CenterAlignedTopAppBar(
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                titleContentColor = MaterialTheme.colorScheme.primary
            ),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = "Gemma Racing",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GEMMA RACING",
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        )

        // ---------- CHAT LIST ----------
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                if (message.isUser) {
                    UserMessageBubble(message)
                } else {
                    AgentMessageBubble(message.text)
                }
            }

            if (isLoading) {
                item {
                    AgentMessageBubble("Thinking...")
                }
            }
        }

        // ---------- INPUT ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "User",
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Pit stop... type here", fontStyle = FontStyle.Italic) },
                enabled = !isLoading,
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = CheckeredGray
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                enabled = !isLoading,
                shape = MaterialTheme.shapes.small,
                onClick = {
                    val userMessage = inputText.trim()
                    if (userMessage.isNotEmpty()) {
                        messages.add(ChatMessage(userMessage, true))
                        inputText = ""
                        isLoading = true

                        scope.launch {
                            val reply = withContext(Dispatchers.IO) {
                                try {
                                    gemmaManager.initialize()
                                    gemmaManager.reply(userMessage)
                                } catch (e: Exception) {
                                    "Model error: ${e.message}"
                                }
                            }

                            messages.add(ChatMessage(reply, false))
                            isLoading = false
                        }
                    }
                }
            ) {
                Text("GO!", fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic)
            }
        }
    }
}

// ---------- BUBBLES ----------

@Composable
fun UserMessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f, fill = false)) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                    .padding(14.dp)
            ) {
                Text(
                    text = message.text,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = "Driver",
            tint = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun AgentMessageBubble(markdown: String) {
    val context = LocalContext.current
    val markwon = remember(context) { Markwon.create(context) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.DirectionsCar,
            contentDescription = "Gemma",
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                .padding(14.dp)
                .weight(1f, fill = false)
        ) {
            AndroidView(
                factory = { ctx -> 
                    TextView(ctx).apply { 
                        setTextColor(android.graphics.Color.WHITE) 
                        textSize = 16f
                    } 
                },
                update = { tv -> markwon.setMarkdown(tv, markdown) }
            )
        }
    }
}
