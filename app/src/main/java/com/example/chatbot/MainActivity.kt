package com.example.kotlin_chatbot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatbot.core.AudioDeliveryManager
import com.example.chatbot.core.GatedInferenceEngine
import com.example.chatbot.core.Gemma4Manager
import com.example.chatbot.core.LatencyTracker
import com.example.chatbot.core.TelemetryInputSource
import com.example.chatbot.core.TelemetryManager
import com.example.chatbot.core.TelemetrySettings
import com.example.chatbot.models.CoachingPayload
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                    DashboardScreen()
                }
            }
        }
    }
}

// ---------- UI ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Core Managers
    val telemetryManager = remember { TelemetryManager() }
    val gatedInferenceEngine = remember { GatedInferenceEngine() }
    val gemmaManager = remember { Gemma4Manager(context) }
    val audioDeliveryManager = remember { AudioDeliveryManager(context) }
    val gson = remember { Gson() }

    // State
    var speed by remember { mutableStateOf(0.0) }
    var steering by remember { mutableStateOf(0.0) }
    var isThinking by remember { mutableStateOf(false) }
    var latestCoaching by remember { mutableStateOf<CoachingPayload?>(null) }
    var systemStatus by remember { mutableStateOf("Initializing Gemma...") }
    var telemetrySource by rememberSaveable { mutableStateOf(TelemetryInputSource.WEBSOCKET_TESTING) }
    var webSocketUrl by rememberSaveable { mutableStateOf("ws://10.0.2.2:8000/ws/telemetry") }
    var canBitrate by rememberSaveable { mutableStateOf("500000") }
    var isTelemetryReceiving by rememberSaveable { mutableStateOf(false) }
    var previousTelemetrySource by remember { mutableStateOf(telemetrySource) }

    // Lifecycle
    DisposableEffect(Unit) {
        scope.launch {
            withContext(Dispatchers.IO) {
                gemmaManager.initialize()
            }
            systemStatus = "Ready & Waiting for Telemetry"
        }

        onDispose {
            telemetryManager.stopListening()
            gemmaManager.close()
            audioDeliveryManager.shutdown()
        }
    }

    LaunchedEffect(telemetrySource, webSocketUrl, canBitrate, isTelemetryReceiving) {
        if (isTelemetryReceiving) {
            val parsedBitrate = canBitrate.toIntOrNull() ?: 500_000
            telemetryManager.startListening(
                context,
                TelemetrySettings(
                    source = telemetrySource,
                    webSocketUrl = webSocketUrl,
                    canBitrate = parsedBitrate
                )
            )
        } else {
            telemetryManager.stopListening()
        }
    }

    LaunchedEffect(telemetrySource) {
        if (telemetrySource != previousTelemetrySource) {
            isTelemetryReceiving = false
            speed = 0.0
            steering = 0.0
            previousTelemetrySource = telemetrySource
        }
    }

    LaunchedEffect(Unit) {
        telemetryManager.connectionStatus.collect { status ->
            systemStatus = status
        }
    }

    // Telemetry Pipeline
    LaunchedEffect(Unit) {
        telemetryManager.telemetryFlow.collect { packet ->
            // Update UI
            speed = packet.speed ?: 0.0
            steering = packet.steering ?: 0.0
            
            // Route to Engine
            gatedInferenceEngine.processTelemetry(packet)
        }
    }

    // Inference Pipeline
    LaunchedEffect(Unit) {
        gatedInferenceEngine.inferenceTriggerFlow.collect { packet ->
            systemStatus = "Straightaway Detected - Triggering Inference"
            isThinking = true
            LatencyTracker.markTelemetryIngestion()

            val packetJson = gson.toJson(packet)
            
            scope.launch(Dispatchers.IO) {
                val payload = try {
                    gemmaManager.generateCoaching(packetJson)
                } catch (e: Exception) {
                    CoachingPayload("Error generating insight", "NORMAL", "Unknown", 0)
                }

                withContext(Dispatchers.Main) {
                    isThinking = false
                    val latency = LatencyTracker.markAudioPlaybackStarted()
                    val finalPayload = payload.copy(latencyMs = latency)
                    latestCoaching = finalPayload
                    audioDeliveryManager.deliverInstruction(finalPayload)
                    systemStatus = "Instruction Delivered (${latency}ms)"
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ---------- HEADER ----------
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
                        text = "ApexAI Coaching Agent",
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        )

        // ---------- TELEMETRY HUD ----------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TelemetrySettingsCard(
                source = telemetrySource,
                webSocketUrl = webSocketUrl,
                canBitrate = canBitrate,
                isReceiving = isTelemetryReceiving,
                onSourceChange = { telemetrySource = it },
                onWebSocketUrlChange = { webSocketUrl = it },
                onCanBitrateChange = { canBitrate = it },
                onStart = { isTelemetryReceiving = true },
                onStop = {
                    isTelemetryReceiving = false
                    speed = 0.0
                    steering = 0.0
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = CarbonGray)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.Speed, contentDescription = "Speed", tint = TrackWhite)
                        Text(text = "SPEED", fontSize = 12.sp, color = CheckeredGray)
                        Text(text = "%.1f km/h".format(speed), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = RacingRed)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "STEERING", fontSize = 12.sp, color = CheckeredGray)
                        Text(text = "%.2f°".format(steering), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TrackWhite)
                    }
                }
            }
        }

        // ---------- STATUS & COACHING ----------
        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isThinking) {
                CircularProgressIndicator(color = RacingRed)
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                text = systemStatus,
                color = CheckeredGray,
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            latestCoaching?.let { payload ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = if (payload.urgency == "HIGH") RacingRed else AsphaltBlack)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = payload.targetCorner.uppercase(),
                            color = if (payload.urgency == "HIGH") TrackWhite else RacingRed,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = payload.instruction,
                            color = TrackWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            lineHeight = 34.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Latency: ${payload.latencyMs}ms",
                            color = CheckeredGray,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TelemetrySettingsCard(
    source: TelemetryInputSource,
    webSocketUrl: String,
    canBitrate: String,
    isReceiving: Boolean,
    onSourceChange: (TelemetryInputSource) -> Unit,
    onWebSocketUrlChange: (String) -> Unit,
    onCanBitrateChange: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CarbonGray)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Telemetry settings",
                    tint = RacingRed
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Telemetry Source",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onStart,
                    enabled = !isReceiving,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Start receiving")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Start")
                }
                OutlinedButton(
                    onClick = onStop,
                    enabled = isReceiving,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop receiving")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stop")
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(if (isReceiving) "Receiving enabled" else "Receiving stopped") },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = if (isReceiving) RacingRed else CheckeredGray
                    )
                )
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = source == TelemetryInputSource.WEBSOCKET_TESTING,
                    onClick = { onSourceChange(TelemetryInputSource.WEBSOCKET_TESTING) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("WebSocket") }
                )
                SegmentedButton(
                    selected = source == TelemetryInputSource.USB_CAN_REALTIME,
                    onClick = { onSourceChange(TelemetryInputSource.USB_CAN_REALTIME) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("USB-C CAN") }
                )
            }

            if (source == TelemetryInputSource.WEBSOCKET_TESTING) {
                OutlinedTextField(
                    value = webSocketUrl,
                    onValueChange = onWebSocketUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("WebSocket URL") },
                    singleLine = true,
                    supportingText = { Text("Use 10.0.2.2 for emulator localhost, or laptop IP for a phone.") }
                )
            } else {
                OutlinedTextField(
                    value = canBitrate,
                    onValueChange = { value -> onCanBitrateChange(value.filter(Char::isDigit)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("CAN bitrate") },
                    singleLine = true,
                    supportingText = { Text("USB-C mode detects adapters now; frame decoding depends on adapter protocol.") }
                )
            }
        }
    }
}
