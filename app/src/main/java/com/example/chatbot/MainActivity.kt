package com.example.kotlin_chatbot

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
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
import com.example.chatbot.core.MemoryBankManager
import com.example.chatbot.core.TelemetryManager
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
    val memoryBankManager = remember { MemoryBankManager(context) }
    val gson = remember { Gson() }
    val sharedPrefs = remember { context.getSharedPreferences("RacingPrefs", Context.MODE_PRIVATE) }

    // State
    var speed by remember { mutableStateOf(0.0) }
    var steering by remember { mutableStateOf(0.0) }
    var isThinking by remember { mutableStateOf(false) }
    var latestCoaching by remember { mutableStateOf<CoachingPayload?>(null) }
    var systemStatus by remember { mutableStateOf("Initializing Gemma...") }

    // Navigation & Memory Bank State
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0: AI Coach, 1: Memory Bank
    var activeSectorId by remember { mutableStateOf<Int?>(null) }
    var rulesLoaded by remember { mutableStateOf(false) }
    var isFetchingRules by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var availableFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var isFetchingFiles by remember { mutableStateOf(false) }
    var selectedFile by rememberSaveable { mutableStateOf<String?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var sessionActive by rememberSaveable { mutableStateOf(false) }

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

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1 && availableFiles.isEmpty()) {
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
    }

    // Telemetry Pipeline
    LaunchedEffect(selectedTab, sessionActive) {
        telemetryManager.telemetryFlow.collect { packet ->
            // Update UI
            speed = packet.speed ?: 0.0
            steering = packet.steering ?: 0.0
            
            if (sessionActive) {
                if (selectedTab == 0) {
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
    LaunchedEffect(Unit) {
        gatedInferenceEngine.inferenceTriggerFlow.collect { packet ->
            if (selectedTab != 0) return@collect
            
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
    LaunchedEffect(Unit) {
        memoryBankManager.coachingFlow.collect { payload ->
            if (selectedTab != 1) return@collect
            latestCoaching = payload
            audioDeliveryManager.deliverInstruction(payload)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { 
                        selectedTab = 0 
                        latestCoaching = null 
                    },
                    icon = { Icon(Icons.Default.SmartToy, contentDescription = "AI Coach") },
                    label = { Text("AI Coach") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = CheckeredGray,
                        indicatorColor = MaterialTheme.colorScheme.background
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { 
                        selectedTab = 1 
                        latestCoaching = null 
                    },
                    icon = { Icon(Icons.Default.Memory, contentDescription = "Memory Bank") },
                    label = { Text("Memory Bank") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = CheckeredGray,
                        indicatorColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
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
                            text = "GEMMA RACING DASHBOARD",
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
                Button(
                    onClick = { 
                        sessionActive = !sessionActive
                        if (!sessionActive) latestCoaching = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (sessionActive) CarbonGray else RacingRed
                    ),
                    modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth()
                ) {
                    Text(if (sessionActive) "STOP COACHING SESSION" else "START COACHING SESSION", color = TrackWhite, fontWeight = FontWeight.Bold)
                }

                if (selectedTab == 1) {
                    // Memory Bank specific UI
                    
                    if (isFetchingFiles) {
                        CircularProgressIndicator(color = RacingRed)
                        Spacer(modifier = Modifier.height(16.dp))
                    } else if (availableFiles.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = it },
                            modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth(0.8f)
                        ) {
                            OutlinedTextField(
                                value = selectedFile ?: "Select a file",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                                    focusedBorderColor = RacingRed,
                                    unfocusedBorderColor = CheckeredGray,
                                    focusedTextColor = TrackWhite,
                                    unfocusedTextColor = TrackWhite
                                ),
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                availableFiles.forEach { file ->
                                    DropdownMenuItem(
                                        text = { Text(file) },
                                        onClick = {
                                            selectedFile = file
                                            dropdownExpanded = false
                                            rulesLoaded = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Text("No files found in bucket.", color = CheckeredGray)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Button(
                        onClick = { 
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
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CarbonGray),
                        modifier = Modifier.padding(bottom = 16.dp),
                        enabled = selectedFile != null
                    ) {
                        Text(if (isFetchingRules) "Fetching..." else "Load Memory Bank", color = TrackWhite)
                    }

                    if (rulesLoaded) {
                        val statusText = if (activeSectorId != null) {
                            "Monitoring Sector $activeSectorId"
                        } else {
                            "Waiting for Track Coordinates..."
                        }
                        Text(
                            text = statusText,
                            color = RacingRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    } else if (!isFetchingRules) {
                        Text(errorMessage ?: "No Rules Loaded. Tap Load.", color = if (errorMessage != null) RacingRed else CheckeredGray)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    // AI Coach specific UI
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
                }

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
                            if (payload.latencyMs > 0) {
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
    }
}
