package com.example.kotlin_chatbot

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatbot.core.AudioDeliveryManager
import com.example.chatbot.core.GatedInferenceEngine
import com.example.chatbot.core.Gemma4Manager
import com.example.chatbot.core.LatencyTracker
import com.example.chatbot.core.MemoryBankManager
import com.example.chatbot.core.TelemetryManager
import com.example.chatbot.models.CoachingPayload
import com.example.chatbot.models.TelemetryPacket
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------- THEME ----------

internal val BackgroundDark = Color(0xFF070B0E)      // Deep Titanium Slate
internal val SurfaceDark = Color(0xFF0F151C)         // Dark Slate Carbon Card
internal val SurfaceBorder = Color(0xFF1E2833)       // Sleek Steel Border
internal val NeonRed = Color(0xFFFF1744)             // Racing Red
internal val NeonCyan = Color(0xFF00E5FF)            // Telemetry Cyan/Blue
internal val NeonGreen = Color(0xFF00E676)           // High performance green
internal val NeonOrange = Color(0xFFFF9100)          // Apex corner warning orange
internal val PurpleAura = Color(0xFFD500F9)          // Gemma local model AI aura
internal val CoolSteel = Color(0xFF607D8B)           // Standby cool steel grey
internal val TrackWhite = Color(0xFFFFFFFF)
internal val CheckeredGray = Color(0xFF9E9E9E)

internal val RacingColorScheme = darkColorScheme(
    primary = NeonRed,
    onPrimary = TrackWhite,
    secondary = NeonCyan,
    onSecondary = BackgroundDark,
    surface = SurfaceDark,
    onSurface = TrackWhite,
    background = BackgroundDark,
    onBackground = TrackWhite,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = TrackWhite
)

internal val RacingShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp)
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
    val memoryBankManager = remember { MemoryBankManager(context) }
    val gson = remember { Gson() }

    // State
    var speed by remember { mutableStateOf(0.0) }
    var steering by remember { mutableStateOf(0.0) }
    var throttle by remember { mutableStateOf(0.0) }
    var brake by remember { mutableStateOf(0.0) }
    var gear by remember { mutableStateOf(0) }
    var lap by remember { mutableStateOf(1) }
    var shockPots by remember { mutableStateOf<List<Double>>(emptyList()) }
    var tireSlipVectors by remember { mutableStateOf<List<Double>>(emptyList()) }
    var wheelSpeedDeltas by remember { mutableStateOf<List<Double>>(emptyList()) }
    
    var isThinking by remember { mutableStateOf(false) }
    var latestCoaching by remember { mutableStateOf<CoachingPayload?>(null) }
    var systemStatus by remember { mutableStateOf("Initializing Gemma...") }

    // Navigation & Config States
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0: Coaching, 1: Debug, 2: Settings
    var coachingEngine by rememberSaveable { mutableStateOf("gemma") } // "gemma" vs "memory_bank"
    var packetCount by remember { mutableLongStateOf(0L) }
    
    var activeSectorId by remember { mutableStateOf<Int?>(null) }
    var rulesLoaded by remember { mutableStateOf(false) }
    var isFetchingRules by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var availableFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var isFetchingFiles by remember { mutableStateOf(false) }
    var selectedFile by rememberSaveable { mutableStateOf<String?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var sessionActive by rememberSaveable { mutableStateOf(false) }

    // Telemetry Recording States
    var isRecording by rememberSaveable { mutableStateOf(false) }
    val recordedPackets = remember { mutableStateListOf<TelemetryPacket>() }
    val currentIsRecording by rememberUpdatedState(isRecording)

    // Lifecycle
    DisposableEffect(Unit) {
        telemetryManager.startListening() // Connects to apexai websocket
        
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

    // Load available files once on start
    LaunchedEffect(Unit) {
        isFetchingFiles = true
        memoryBankManager.fetchAvailableFiles { files ->
            scope.launch(Dispatchers.Main) {
                isFetchingFiles = false
                availableFiles = files
                if (files.isNotEmpty() && selectedFile == null) {
                    selectedFile = files.first()
                }
            }
        }
    }

    // Telemetry Pipeline
    LaunchedEffect(sessionActive, coachingEngine) {
        telemetryManager.telemetryFlow.collect { packet ->
            packetCount++
            // Update UI
            speed = packet.speed ?: 0.0
            steering = packet.steering ?: 0.0
            throttle = packet.throttle ?: 0.0
            brake = packet.brake ?: 0.0
            gear = packet.gear ?: 0
            lap = packet.lap ?: 1
            shockPots = packet.shockPots ?: emptyList()
            tireSlipVectors = packet.tireSlipVectors ?: emptyList()
            wheelSpeedDeltas = packet.wheelSpeedDeltas ?: emptyList()
            
            if (currentIsRecording) {
                recordedPackets.add(packet)
            }
            
            if (sessionActive) {
                if (coachingEngine == "gemma") {
                    // Route to AI Coach Engine
                    gatedInferenceEngine.processTelemetry(packet)
                } else {
                    // Route to Memory Bank
                    memoryBankManager.processTelemetry(packet)
                    activeSectorId = memoryBankManager.getCurrentSegmentId()
                }
            }
        }
    }

    // AI Coach Inference Pipeline
    LaunchedEffect(coachingEngine) {
        gatedInferenceEngine.inferenceTriggerFlow.collect { packet ->
            if (coachingEngine != "gemma") return@collect
            
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

    // Memory Bank Inference Pipeline
    LaunchedEffect(coachingEngine) {
        memoryBankManager.coachingFlow.collect { payload ->
            if (coachingEngine != "memory_bank") return@collect
            latestCoaching = payload
            audioDeliveryManager.deliverInstruction(payload)
        }
    }

    Scaffold(
        topBar = {
            // Sleek Premium Custom Pinned Header
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(SurfaceDark)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = "Gemma Racing",
                            tint = NeonRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Row {
                            Text(
                                text = "APEX",
                                fontWeight = FontWeight.Black,
                                fontStyle = FontStyle.Italic,
                                fontSize = 13.sp,
                                color = NeonRed,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "AI COACH",
                                fontWeight = FontWeight.Light,
                                fontStyle = FontStyle.Normal,
                                fontSize = 13.sp,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SONOMA",
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp,
                            letterSpacing = 1.sp,
                            color = NeonCyan,
                            modifier = Modifier
                                .border(width = 0.5.dp, color = NeonCyan.copy(alpha = 0.4f), shape = RoundedCornerShape(4.dp))
                                .background(NeonCyan.copy(alpha = 0.08f), shape = RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .border(width = 0.5.dp, color = SurfaceBorder, shape = RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(NeonGreen, shape = RoundedCornerShape(2.5.dp))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "PRO V2.1",
                            color = Color.LightGray,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                // Horizontal Neon Gradient Divider line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(NeonRed, NeonCyan, NeonGreen)
                             )
                        )
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Speed, contentDescription = "Coaching") },
                    label = { Text("Coaching") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = CheckeredGray,
                        indicatorColor = MaterialTheme.colorScheme.background
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Build, contentDescription = "Debug") },
                    label = { Text("Debug") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = CheckeredGray,
                        indicatorColor = MaterialTheme.colorScheme.background
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = CheckeredGray,
                        indicatorColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
        ) {
            // Render current page content based on tab index
            when (selectedTab) {
                0 -> CoachingPage(
                    speed = speed,
                    steering = steering,
                    throttle = throttle,
                    brake = brake,
                    sessionActive = sessionActive,
                    isThinking = isThinking,
                    systemStatus = systemStatus,
                    latestCoaching = latestCoaching,
                    onToggleSession = {
                        sessionActive = !sessionActive
                        if (!sessionActive) latestCoaching = null
                    }
                )
                1 -> DebugPage(
                    speed = speed,
                    steering = steering,
                    throttle = throttle,
                    brake = brake,
                    gear = gear,
                    lap = lap,
                    packetCount = packetCount,
                    sessionActive = sessionActive,
                    coachingEngine = coachingEngine,
                    shockPots = shockPots,
                    tireSlip = tireSlipVectors,
                    wheelDeltas = wheelSpeedDeltas,
                    systemStatus = systemStatus,
                    isRecording = isRecording,
                    onRecordingChange = { isRecording = it },
                    recordedPackets = recordedPackets,
                    onClearRecording = {
                        isRecording = false
                        recordedPackets.clear()
                    }
                )
                2 -> SettingsPage(
                    coachingEngine = coachingEngine,
                    onEngineChange = { coachingEngine = it },
                    selectedFile = selectedFile,
                    dropdownExpanded = dropdownExpanded,
                    onDropdownExpandedChange = { dropdownExpanded = it },
                    availableFiles = availableFiles,
                    isFetchingFiles = isFetchingFiles,
                    isFetchingRules = isFetchingRules,
                    rulesLoaded = rulesLoaded,
                    activeSectorId = activeSectorId,
                    errorMessage = errorMessage,
                    onFileSelected = {
                        selectedFile = it
                        rulesLoaded = false
                    },
                    onLoadRules = {
                        selectedFile?.let { file ->
                            isFetchingRules = true
                            errorMessage = null
                            memoryBankManager.fetchRules(file) { success ->
                                scope.launch(Dispatchers.Main) {
                                    isFetchingRules = false
                                    rulesLoaded = success
                                    if (!success) {
                                        errorMessage = "Failed to fetch rules. Check if bucket is public."
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}
