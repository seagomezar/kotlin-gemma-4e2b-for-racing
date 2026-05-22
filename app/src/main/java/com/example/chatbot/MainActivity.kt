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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.core.*
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

private val BackgroundDark = Color(0xFF070B0E)      // Deep Titanium Slate
private val SurfaceDark = Color(0xFF0F151C)         // Dark Slate Carbon Card
private val SurfaceBorder = Color(0xFF1E2833)       // Sleek Steel Border
private val NeonRed = Color(0xFFFF1744)             // Racing Red
private val NeonCyan = Color(0xFF00E5FF)            // Telemetry Cyan/Blue
private val NeonGreen = Color(0xFF00E676)           // High performance green
private val NeonOrange = Color(0xFFFF9100)          // Apex corner warning orange
private val PurpleAura = Color(0xFFD500F9)          // Gemma local model AI aura
private val CoolSteel = Color(0xFF607D8B)           // Standby cool steel grey
private val TrackWhite = Color(0xFFFFFFFF)
private val CheckeredGray = Color(0xFF9E9E9E)

private val RacingColorScheme = darkColorScheme(
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

private val RacingShapes = Shapes(
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

// ---------- WIDGETS ----------

@Composable
fun CircularSpeedometerGauge(speed: Double, maxSpeed: Double = 300.0, modifier: Modifier = Modifier) {
    val speedSweep = ((speed / maxSpeed) * 240.0).coerceIn(0.0, 240.0).toFloat()
    
    Box(
        modifier = modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Background track arc
            drawArc(
                color = SurfaceBorder,
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            )
            // Foreground speed arc (gradient)
            drawArc(
                brush = Brush.sweepGradient(
                    0f to NeonCyan,
                    0.5f to NeonGreen,
                    1f to NeonRed
                ),
                startAngle = 150f,
                sweepAngle = speedSweep,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "SPEED",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.LightGray,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "%.0f".format(speed),
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
            Text(
                text = "KM/H",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = NeonCyan
            )
        }
    }
}

@Composable
fun SteeringHud(steering: Double, modifier: Modifier = Modifier) {
    val steeringClamped = steering.coerceIn(-1.0, 1.0).toFloat()
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "STEERING L", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
            Text(
                text = "%.2f°".format(steering * 180.0),
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = if (kotlin.math.abs(steering) > 0.5) NeonOrange else Color.White
            )
            Text(text = "R STEERING", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
        }
        Spacer(modifier = Modifier.height(6.dp))
        
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
        ) {
            drawRoundRect(
                color = SurfaceBorder,
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
            )
            
            val barCenter = size.width / 2f
            val barWidth = (steeringClamped * (size.width / 2f))
            
            drawRect(
                color = if (kotlin.math.abs(steeringClamped) > 0.6f) NeonOrange else NeonCyan,
                topLeft = androidx.compose.ui.geometry.Offset(
                    if (barWidth < 0) barCenter + barWidth else barCenter,
                    0f
                ),
                size = androidx.compose.ui.geometry.Size(
                    kotlin.math.abs(barWidth),
                    size.height
                )
            )
            
            drawLine(
                color = Color.White,
                start = androidx.compose.ui.geometry.Offset(barCenter, -4f),
                end = androidx.compose.ui.geometry.Offset(barCenter, size.height + 4f),
                strokeWidth = 3.dp.toPx()
            )
        }
    }
}

@Composable
fun PedalsTelemetry(throttle: Double, brake: Double, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "COCKPIT CONTROL INPUTS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.LightGray,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "THROTTLE", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(text = "${(throttle * 100).toInt()}%", fontSize = 10.sp, color = NeonGreen, fontWeight = FontWeight.Black)
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { throttle.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = NeonGreen,
                    trackColor = SurfaceBorder
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "BRAKE", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(text = "${(brake * 100).toInt()}%", fontSize = 10.sp, color = NeonRed, fontWeight = FontWeight.Black)
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { brake.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = NeonRed,
                    trackColor = SurfaceBorder
                )
            }
        }
    }
}

@Composable
fun GearLapHud(gear: Int, lap: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(Color.Black, shape = RoundedCornerShape(8.dp))
                .border(BorderStroke(1.5.dp, NeonRed), shape = RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "GEAR", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Text(
                    text = if (gear == 0) "N" else gear.toString(),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = if (gear == 0) NeonGreen else Color.White
                )
            }
        }
        
        Box(
            modifier = Modifier
                .height(60.dp)
                .width(80.dp)
                .background(Color.Black, shape = RoundedCornerShape(8.dp))
                .border(BorderStroke(1.5.dp, SurfaceBorder), shape = RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "LAP", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Text(
                    text = "%02d".format(lap),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun ProSensorTelemetryGrid(
    shockPots: List<Double>,
    tireSlip: List<Double>,
    wheelDeltas: List<Double>,
    modifier: Modifier = Modifier
) {
    val fallbackShock = remember { listOf(42.5, 40.2, 45.1, 41.8) }
    val fallbackSlip = remember { listOf(0.02, 0.01, 0.03, 0.02) }
    val fallbackDeltas = remember { listOf(-1.2, 0.5, 0.8, -0.4) }
    
    val finalShock = if (shockPots.isNotEmpty()) shockPots else fallbackShock
    val finalSlip = if (tireSlip.isNotEmpty()) tireSlip else fallbackSlip
    val finalDeltas = if (wheelDeltas.isNotEmpty()) wheelDeltas else fallbackDeltas

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, SurfaceBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "PRO SENSOR CHANNELS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = NeonCyan,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(0.4f)) {
                        Text(text = "SHOCK POTS (FL/FR/RL/RR)", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text(
                            text = finalShock.joinToString(" | ") { "%.1fmm".format(it) },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    val avgShock = finalShock.average().toFloat().coerceIn(0f, 100f)
                    LinearProgressIndicator(
                        progress = { avgShock / 100f },
                        modifier = Modifier
                            .weight(0.6f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = NeonCyan,
                        trackColor = SurfaceBorder
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(0.4f)) {
                        Text(text = "TIRE SLIP VECTORS", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text(
                            text = finalSlip.joinToString(" | ") { "%.2f".format(it) },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    val avgSlip = finalSlip.average().toFloat().coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { avgSlip * 5f },
                        modifier = Modifier
                            .weight(0.6f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = NeonOrange,
                        trackColor = SurfaceBorder
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(0.4f)) {
                        Text(text = "WHEEL SPEED DELTAS", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text(
                            text = finalDeltas.joinToString(" | ") { "%.1f".format(it) },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    val avgDelta = kotlin.math.abs(finalDeltas.average()).toFloat().coerceIn(0f, 5f)
                    LinearProgressIndicator(
                        progress = { avgDelta / 5f },
                        modifier = Modifier
                            .weight(0.6f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = NeonRed,
                        trackColor = SurfaceBorder
                    )
                }
            }
        }
    }
}

@Composable
fun GemmaThinkingOrb(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    Box(
        modifier = modifier.size(80.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = glowAlpha
        }) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(PurpleAura, Color.Transparent),
                    radius = size.width / 2f
                ),
                radius = size.width / 2f
            )
        }
        
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White, PurpleAura)
                    ),
                    shape = RoundedCornerShape(18.dp)
                )
                .border(BorderStroke(1.5.dp, Color.White), shape = RoundedCornerShape(18.dp))
        )
    }
}

@Composable
fun GlassmorphicCoachingCard(payload: CoachingPayload, modifier: Modifier = Modifier) {
    val isHighUrgency = payload.urgency == "HIGH"
    val accentColor = if (isHighUrgency) NeonRed else NeonCyan
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = accentColor,
                spotColor = accentColor
            ),
        colors = CardDefaults.cardColors(containerColor = Color(0x1FFFFFFF)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(accentColor, SurfaceBorder)))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x0AFFFFFF),
                            Color(0x1F000000)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .background(
                            color = if (isHighUrgency) NeonRed.copy(alpha = 0.2f) else NeonCyan.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = accentColor,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(accentColor, shape = RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isHighUrgency) "HIGH URGENCY APEX ALERT" else "NORMAL RACING COACHING",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                
                Text(
                    text = payload.targetCorner.uppercase(),
                    color = accentColor,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    letterSpacing = 1.5.sp
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = payload.instruction,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (payload.latencyMs > 0) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "LATENCY: ${payload.latencyMs}ms",
                        color = Color.LightGray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
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
            throttle = packet.throttle ?: 0.0
            brake = packet.brake ?: 0.0
            gear = packet.gear ?: 0
            lap = packet.lap ?: 1
            shockPots = packet.shockPots ?: emptyList()
            tireSlipVectors = packet.tireSlipVectors ?: emptyList()
            wheelSpeedDeltas = packet.wheelSpeedDeltas ?: emptyList()
            
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
                colors = TopAppBarDefaults.topAppBarColors(
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
                                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
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
