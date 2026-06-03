package com.example.kotlin_chatbot

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.chatbot.core.AudioDeliveryManager
import com.example.chatbot.core.CanableCarDataReader
import com.example.chatbot.core.CanableStatus
import com.example.chatbot.core.GemmaCoach
import com.example.chatbot.core.Gemma4Lite
import com.example.chatbot.core.HeuristicBaseAICoach
import com.example.chatbot.core.MemoryBank
import com.example.chatbot.core.MemoryBankFileStore
import com.example.chatbot.core.TelemetryManager
import com.example.chatbot.models.CoachingPayload
import com.example.chatbot.models.TelemetryPacket
import com.google.ai.edge.litertlm.Backend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File

private fun Context.appendCanValidationLine(fileName: String, value: String): File {
    val directory = getExternalFilesDir(null) ?: filesDir
    val file = File(directory, fileName)
    file.appendText("${System.currentTimeMillis()},$value\n")
    return file
}

private fun newCanLogSessionPrefix(source: String): String {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
    return "${source}_$timestamp"
}

private fun ModelBackendOption.toLiteRtBackend(context: Context): Backend {
    return when (this) {
        ModelBackendOption.CPU -> Backend.CPU()
        ModelBackendOption.GPU -> Backend.GPU()
        ModelBackendOption.NPU -> Backend.NPU(
            nativeLibraryDir = context.applicationInfo.nativeLibraryDir
        )
    }
}

private fun ModelBackendOption.modelPath(): String {
    return when (this) {
        ModelBackendOption.CPU,
        ModelBackendOption.GPU -> GEMMA4_MODEL_PATH
        ModelBackendOption.NPU -> GEMMA3_TENSOR_G5_NPU_MODEL_PATH
    }
}

private fun ModelBackendOption.modelName(): String {
    return when (this) {
        ModelBackendOption.CPU,
        ModelBackendOption.GPU -> "Gemma 4 E2B IT"
        ModelBackendOption.NPU -> "Gemma 3 1B IT Tensor G5"
    }
}

private fun Context.hasNpuDispatchRuntime(): Boolean {
    val nativeLibraryDir = File(applicationInfo.nativeLibraryDir)
    return nativeLibraryDir
        .listFiles()
        ?.any { file -> file.name.contains("dispatch", ignoreCase = true) && file.extension == "so" }
        ?: false
}

private data class GemmaStructuredOutputTest(
    val shouldSpeak: Boolean = false,
    val command: String = "",
    val reason: String = ""
)

private const val GEMMA4_MODEL_PATH = "/data/local/tmp/llm/gemma-4-E2B-it.litertlm"
private const val GEMMA3_TENSOR_G5_NPU_MODEL_PATH =
    "/data/local/tmp/llm/gemma3-tensor-g5-npu.litertlm"
private const val MIN_GEMMA3_TENSOR_G5_NPU_MODEL_BYTES = 1_000_000_000L
private const val MODEL_CHAT_SYSTEM_PROMPT = "You are a helpful assistant."
private val GEMMA_STRUCTURED_OUTPUT_TEST_SYSTEM_PROMPT = """
    You are testing structured JSON output for an expert real-time racing coach.
    Compare current telemetry against sector memory annotations.
    Respond only with a JSON object matching this schema:
    {
      "shouldSpeak": true,
      "command": "Short audio coaching command.",
      "reason": "Brief reason."
    }
    Set shouldSpeak=true only when telemetry violates a condition.
    Do not include markdown or extra text.
""".trimIndent()

@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val telemetryManager = remember { TelemetryManager() }
    val gemmaManager = remember { Gemma4Lite(context) }
    val audioDeliveryManager = remember { AudioDeliveryManager(context) }
    val memoryBank = remember { MemoryBank(context) }
    val memoryBankFileStore = remember { MemoryBankFileStore(context) }
    val heuristicCoach = remember { HeuristicBaseAICoach(context, audioDeliveryManager) }
    val gemmaCoach = remember { GemmaCoach(context, gemmaManager, audioDeliveryManager) }
    val canableReader = remember { CanableCarDataReader(context) }
    val canableStatus by canableReader.statusFlow.collectAsState()

    var speed by remember { mutableStateOf(0.0) }
    var steering by remember { mutableStateOf(0.0) }
    var isThinking by remember { mutableStateOf(false) }
    var latestCoaching by remember { mutableStateOf<CoachingPayload?>(null) }
    var latestTelemetry by remember { mutableStateOf<TelemetryPacket?>(null) }
    var canReading by rememberSaveable { mutableStateOf(false) }
    var canOutput by remember { mutableStateOf("No CAN data yet. Connect CANable and tap Start CAN Read.") }
    var rawCanOutput by remember { mutableStateOf("No raw CAN frames yet.") }
    var rawHexOutput by remember { mutableStateOf("No USB bytes yet.") }
    var rawExportPath by remember { mutableStateOf("Raw frames file will be created after the first frame.") }
    var canLogSessionPrefix by rememberSaveable { mutableStateOf<String?>(null) }
    val currentCanLogSessionPrefix by rememberUpdatedState(canLogSessionPrefix)

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(DashboardTab.AiCoach.ordinal) }
    val selectedTab = DashboardTab.fromIndex(selectedTabIndex)
    val currentSelectedTab by rememberUpdatedState(selectedTab)
    var selectedCoachType by rememberSaveable { mutableStateOf(CoachType.HEURISTIC) }
    val currentCoachType by rememberUpdatedState(selectedCoachType)
    var coachTypeDropdownExpanded by remember { mutableStateOf(false) }
    var activeSectorId by remember { mutableStateOf<Int?>(null) }
    var rulesLoaded by remember { mutableStateOf(false) }
    var isFetchingRules by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var availableFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var downloadedMemoryBankFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var isFetchingFiles by remember { mutableStateOf(false) }
    var selectedFile by rememberSaveable { mutableStateOf<String?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var selectedDownloadedMemoryBankFile by rememberSaveable { mutableStateOf<String?>(null) }
    var aiCoachMemoryBankDropdownExpanded by remember { mutableStateOf(false) }
    var sessionActive by rememberSaveable { mutableStateOf(false) }
    val currentSessionActive by rememberUpdatedState(sessionActive)
    var rawJson by remember { mutableStateOf<String?>(null) }
    var isFetchingJson by remember { mutableStateOf(false) }
    var showJsonDialog by remember { mutableStateOf(false) }
    var chatMessages by remember { mutableStateOf<List<ModelChatMessage>>(emptyList()) }
    var chatInput by rememberSaveable { mutableStateOf("") }
    var chatBackend by rememberSaveable { mutableStateOf(ModelBackendOption.CPU) }
    var chatMtpEnabled by rememberSaveable { mutableStateOf(false) }
    var chatGenerating by remember { mutableStateOf(false) }
    var chatGeneratingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var chatFirstTokenLatencyMs by remember { mutableStateOf<Long?>(null) }
    var chatTotalLatencyMs by remember { mutableStateOf<Long?>(null) }
    var chatErrorMessage by remember { mutableStateOf<String?>(null) }

    val toggleCanRead = {
        if (canReading && canableStatus != CanableStatus.SIMULATING) {
            canableReader.stop()
            canReading = false
            canOutput = "CAN read stopped."
        } else {
            canLogSessionPrefix = newCanLogSessionPrefix("usb")
            canOutput = "Starting CANable reader..."
            rawExportPath = "New USB log session starting..."
            canableReader.start(useSimulator = false)
            canReading = true
        }
    }

    val toggleSimulator = {
        if (canReading && canableStatus == CanableStatus.SIMULATING) {
            canableReader.stop()
            canReading = false
            canOutput = "CAN read stopped."
        } else {
            canLogSessionPrefix = newCanLogSessionPrefix("simulator")
            canOutput = "Connecting to simulator..."
            rawExportPath = "New simulator log session starting..."
            canableReader.start(useSimulator = true)
            canReading = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            canableReader.close()
            telemetryManager.stopListening()
            gemmaManager.close()
            audioDeliveryManager.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        canableReader.rawFrameFlow.collect { rawFrame ->
            rawCanOutput = rawFrame
            val sessionPrefix = currentCanLogSessionPrefix ?: "unspecified"
            val file = withContext(Dispatchers.IO) {
                context.appendCanValidationLine("can_raw_frames_$sessionPrefix.txt", rawFrame)
            }
            rawExportPath = file.absolutePath
        }
    }

    LaunchedEffect(Unit) {
        canableReader.rawHexFlow.collect { rawHex ->
            rawHexOutput = rawHex
            val sessionPrefix = currentCanLogSessionPrefix ?: "unspecified"
            withContext(Dispatchers.IO) {
                context.appendCanValidationLine("can_raw_hex_chunks_$sessionPrefix.txt", rawHex)
            }
        }
    }

    LaunchedEffect(Unit) {
        canableReader.telemetryFlow.collect { packet ->
            latestTelemetry = packet
            speed = packet.speed ?: speed
            steering = packet.steering ?: steering
            activeSectorId = if (packet.latitude != null && packet.longitude != null) {
                memoryBank.getClosestSectorId(packet.latitude, packet.longitude) ?: activeSectorId
            } else {
                activeSectorId
            }
            canOutput = """
                seq=${packet.sequence}
                status=$canableStatus
                rpm=${packet.rpm ?: "-"}
                speed_mph=${packet.speed ?: "-"}
                gear=${packet.gear ?: "-"}
                ecu_ect_f=${packet.waterTempF ?: "-"}
                yaw_rate_dps=${packet.yawRateDps ?: "-"}
                lateral_accel_g=${packet.lateralAccelG ?: "-"}
                inline_accel_g=${packet.inlineAccelG ?: "-"}
                roll_rate_dps=${packet.rollRateDps ?: "-"}
                pitch_rate_dps=${packet.pitchRateDps ?: "-"}
                vertical_accel_g=${packet.verticalAccelG ?: "-"}
                brake_psi=${packet.brakePressurePsi ?: "-"}
                brake_switch=${packet.brakeSwitchApplied ?: "-"}
                ecu_dbw_app1=${packet.ecuDbwApp1Percent?.let { "%.2f%%".format(it) } ?: "-"}
                pedal_percent=${packet.pedalPositionPercent?.let { "%.2f%%".format(it) } ?: "-"}
                lat=${packet.latitude ?: "-"}
                lon=${packet.longitude ?: "-"}
                closest_sector=${activeSectorId ?: "-"}
                closest_sector_distance_m=${packet.latitude?.let { lat -> packet.longitude?.let { lon -> memoryBank.getClosestSectorDistanceMeters(lat, lon)?.let { "%.1f".format(it) } } } ?: "-"}
                ecu_oil_temp_f=${packet.engineOilTempF ?: "-"}
                analog_oil_temp_f=${packet.analogOilTempF ?: "-"}
                oil_pressure_psi=${packet.oilPressurePsi ?: "-"}
                fuel_pressure_psi=${packet.fuelPressurePsi ?: "-"}
                fuel_gal=${packet.fuelLevelGallons ?: "-"}
                ecu_mil_out=${packet.ecuMilOut ?: "-"}
                wheel_speeds_mph=[${packet.wheelSpeedFlMph ?: "-"}, ${packet.wheelSpeedFrMph ?: "-"}, ${packet.wheelSpeedRlMph ?: "-"}, ${packet.wheelSpeedRrMph ?: "-"}]
            """.trimIndent()

            if (currentSelectedTab == DashboardTab.AiCoach && currentSessionActive) {
                when (currentCoachType) {
                    CoachType.HEURISTIC -> {
                        heuristicCoach.onTelemetry(packet)?.let { latestCoaching = it }
                    }
                    CoachType.GEMMA -> {
                        if (!isThinking) {
                            isThinking = true
                            scope.launch(Dispatchers.IO) {
                                val payload = gemmaCoach.onTelemetry(packet)
                                withContext(Dispatchers.Main) {
                                    payload?.let { latestCoaching = it }
                                    isThinking = false
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(canableStatus) {
        when (canableStatus) {
            CanableStatus.NO_DEVICE -> {
                canReading = false
                canOutput = "No CANable USB device found."
            }
            CanableStatus.UNSUPPORTED_DEVICE -> {
                canReading = false
                canOutput = "Android sees a USB device, but no supported serial driver matched it."
            }
            CanableStatus.PERMISSION_DENIED -> {
                canReading = false
                canOutput = "USB permission denied for CANable."
            }
            CanableStatus.ERROR -> {
                canReading = false
                canOutput = "CANable reader error. Check USB connection and try again."
            }
            CanableStatus.WAITING_FOR_PERMISSION -> {
                canOutput = "Waiting for Android USB permission..."
            }
            CanableStatus.CONNECTED -> {
                canOutput = "CANable connected. Waiting for CAN frames..."
            }
            CanableStatus.SIMULATING -> {
                canOutput = "Connected to simulator. Streaming CAN frames..."
            }
            CanableStatus.DISCONNECTED -> Unit
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == DashboardTab.AiCoach || selectedTab == DashboardTab.MemoryBank) {
            downloadedMemoryBankFiles = memoryBankFileStore.getDownloadedMemoryBankFiles()
            selectedDownloadedMemoryBankFile =
                selectedDownloadedMemoryBankFile
                    ?.takeIf { it in downloadedMemoryBankFiles }
                    ?: downloadedMemoryBankFiles.firstOrNull()
        }

        if (selectedTab == DashboardTab.MemoryBank && availableFiles.isEmpty()) {
            isFetchingFiles = true
            memoryBankFileStore.fetchAvailableFiles { files ->
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

    LaunchedEffect(selectedDownloadedMemoryBankFile, selectedCoachType) {
        val filePath = selectedDownloadedMemoryBankFile ?: return@LaunchedEffect
        try {
            withContext(Dispatchers.IO) {
                memoryBank.`init`(filePath)
                heuristicCoach.init(filePath)
                if (selectedCoachType == CoachType.GEMMA) {
                    gemmaCoach.init(filePath)
                }
            }
            rulesLoaded = true
            errorMessage = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            rulesLoaded = false
            errorMessage = "Failed to load memory bank: ${e.message ?: "unknown error"}"
        }
    }

    LaunchedEffect(selectedTab, sessionActive) {
        telemetryManager.telemetryFlow.collect { packet ->
            latestTelemetry = packet
            speed = packet.speed ?: 0.0
            steering = packet.steering ?: 0.0
            activeSectorId = if (packet.latitude != null && packet.longitude != null) {
                memoryBank.getClosestSectorId(packet.latitude, packet.longitude) ?: activeSectorId
            } else {
                activeSectorId
            }

            if (sessionActive) {
                when (selectedTab) {
                    DashboardTab.AiCoach -> {
                        when (currentCoachType) {
                            CoachType.HEURISTIC -> {
                                heuristicCoach.onTelemetry(packet)?.let { latestCoaching = it }
                            }
                            CoachType.GEMMA -> {
                                if (!isThinking) {
                                    isThinking = true
                                    scope.launch(Dispatchers.IO) {
                                        val payload = gemmaCoach.onTelemetry(packet)
                                        withContext(Dispatchers.Main) {
                                            payload?.let { latestCoaching = it }
                                            isThinking = false
                                        }
                                    }
                                }
                            }
                        }
                    }
                    DashboardTab.Chat -> Unit
                    DashboardTab.MemoryBank -> Unit
                    DashboardTab.DataLog -> Unit
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            DashboardBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    selectedTabIndex = tab.ordinal
                    latestCoaching = null
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            RacingHeader()

            when (selectedTab) {
                DashboardTab.AiCoach -> {
                    AiCoachPanel(
                        sessionActive = sessionActive,
                        isThinking = isThinking,
                        canReading = canReading,
                        canableStatus = canableStatus,
                        latestCoaching = latestCoaching,
                        latestTelemetry = latestTelemetry,
                        currentSectorId = activeSectorId,
                        selectedCoachType = selectedCoachType,
                        coachTypeDropdownExpanded = coachTypeDropdownExpanded,
                        downloadedMemoryBankFiles = downloadedMemoryBankFiles,
                        selectedMemoryBankFile = selectedDownloadedMemoryBankFile,
                        memoryBankDropdownExpanded = aiCoachMemoryBankDropdownExpanded,
                        onCoachTypeDropdownExpandedChange = {
                            coachTypeDropdownExpanded = it
                        },
                        onCoachTypeSelected = { coachType ->
                            selectedCoachType = coachType
                            coachTypeDropdownExpanded = false
                            latestCoaching = null
                        },
                        onMemoryBankDropdownExpandedChange = {
                            aiCoachMemoryBankDropdownExpanded = it
                        },
                        onMemoryBankFileSelected = { filePath ->
                            selectedDownloadedMemoryBankFile = filePath
                            aiCoachMemoryBankDropdownExpanded = false
                        },
                        onSessionToggle = {
                            sessionActive = !sessionActive
                            if (!sessionActive) latestCoaching = null
                        },
                        onTestSpeak = {
                            audioDeliveryManager.speak("Speech test is working.")
                        },
                        onTestGemmaStructuredOutput = {
                            if (isThinking) return@AiCoachPanel

                            isThinking = true
                            latestCoaching = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    try {
                                        gemmaManager.initialize(
                                            backend = Backend.CPU(),
                                            enableMtp = true
                                        )
                                        val response = gemmaManager.generateJSON(
                                            prompt = """
                                                Current telemetry:
                                                {
                                                  "speed": 92.0,
                                                  "rpm": 6100,
                                                  "gear": 4,
                                                  "throttle": 88.0,
                                                  "brake_pressure_psi": 0.0,
                                                  "latitude": 38.1609,
                                                  "longitude": -122.4540
                                                }

                                                Closest sector:
                                                1

                                                Sector memory annotations:
                                                [
                                                  {
                                                    "id": "sec_1_overspeed_entry",
                                                    "sector_id": 1,
                                                    "priority": 1,
                                                    "title": "Reduce entry speed",
                                                    "metric": "speed",
                                                    "operator": ">",
                                                    "threshold": 85.0,
                                                    "current_value": 92.0,
                                                    "condition_met": true,
                                                    "optimal_value": 82.0,
                                                    "average_value": 88.0,
                                                    "command": "Slow the entry and brake earlier.",
                                                    "description": "You are carrying too much speed into this sector."
                                                  }
                                                ]

                                                Since speed is 92.0 and the trigger is speed > 85.0,
                                                return shouldSpeak=true and use the annotation command.
                                            """.trimIndent(),
                                            responseClass = GemmaStructuredOutputTest::class.java,
                                            systemPrompt = GEMMA_STRUCTURED_OUTPUT_TEST_SYSTEM_PROMPT
                                        )
                                        Result.success(response)
                                    } catch (e: Exception) {
                                        Result.failure(e)
                                    }
                                }

                                isThinking = false
                                result
                                    .onSuccess { response ->
                                        latestCoaching = CoachingPayload(
                                            instruction = "Gemma JSON parsed: ${response.command}\n${response.reason}",
                                            urgency = if (response.shouldSpeak) "NORMAL" else "LOW",
                                            targetCorner = "Structured Output Test",
                                            latencyMs = 0
                                        )
                                        if (response.shouldSpeak && response.command.isNotBlank()) {
                                            audioDeliveryManager.speak(response.command)
                                        }
                                    }
                                    .onFailure { error ->
                                        latestCoaching = CoachingPayload(
                                            instruction = "Gemma JSON test failed: ${error.message ?: "unknown error"}",
                                            urgency = "HIGH",
                                            targetCorner = "Structured Output Test",
                                            latencyMs = 0
                                        )
                                    }
                            }
                        },
                        onToggleCanRead = toggleCanRead,
                        onToggleSimulator = toggleSimulator
                    )
                }
                DashboardTab.Chat -> ModelChatPanel(
                    messages = chatMessages,
                    input = chatInput,
                    selectedBackend = chatBackend,
                    loadedModelName = chatBackend.modelName(),
                    loadedModelPath = chatBackend.modelPath(),
                    mtpEnabled = chatMtpEnabled && chatBackend != ModelBackendOption.NPU,
                    isGenerating = chatGenerating,
                    firstTokenLatencyMs = chatFirstTokenLatencyMs,
                    totalLatencyMs = chatTotalLatencyMs,
                    errorMessage = chatErrorMessage,
                    onInputChange = { chatInput = it },
                    onBackendSelected = {
                        chatBackend = it
                        if (it == ModelBackendOption.NPU) {
                            chatMtpEnabled = false
                        }
                    },
                    onMtpEnabledChange = { enabled ->
                        if (chatBackend != ModelBackendOption.NPU) {
                            chatMtpEnabled = enabled
                        }
                    },
                    onClearChat = {
                        chatMessages = emptyList()
                        chatFirstTokenLatencyMs = null
                        chatTotalLatencyMs = null
                        chatErrorMessage = null
                    },
                    onStopGeneration = {
                        chatGeneratingJob?.cancel()
                    },
                    onSend = {
                        val prompt = chatInput.trim()
                        if (prompt.isBlank() || chatGenerating) return@ModelChatPanel

                        val selectedBackend = chatBackend
                        val selectedMtpEnabled = chatMtpEnabled && selectedBackend != ModelBackendOption.NPU
                        val nextMessages = chatMessages +
                            ModelChatMessage(ChatRole.User, prompt) +
                            ModelChatMessage(ChatRole.Assistant, "")
                        val assistantIndex = nextMessages.lastIndex

                        chatMessages = nextMessages
                        chatInput = ""
                        chatGenerating = true
                        chatFirstTokenLatencyMs = null
                        chatTotalLatencyMs = null
                        chatErrorMessage = null

                        val job = scope.launch {
                            val startMs = SystemClock.elapsedRealtime()
                            var assistantText = ""
                            var firstTokenRecorded = false
                            val selectedModelPath = selectedBackend.modelPath()

                            try {
                                val modelFile = File(selectedModelPath)
                                if (!modelFile.exists()) {
                                    error("Model file not found: $selectedModelPath")
                                }

                                if (
                                    selectedBackend == ModelBackendOption.NPU &&
                                    modelFile.length() < MIN_GEMMA3_TENSOR_G5_NPU_MODEL_BYTES
                                ) {
                                    error("NPU model file looks incomplete: $selectedModelPath")
                                }

                                if (
                                    selectedBackend == ModelBackendOption.NPU &&
                                    !context.hasNpuDispatchRuntime()
                                ) {
                                    error(
                                        "NPU runtime is missing. LiteRT could not find a dispatch " +
                                            ".so in ${context.applicationInfo.nativeLibraryDir}."
                                    )
                                }

                                withContext(Dispatchers.IO) {
                                    gemmaManager.initialize(
                                        modelPath = selectedModelPath,
                                        backend = selectedBackend.toLiteRtBackend(context),
                                        enableMtp = selectedMtpEnabled
                                    )
                                }

                                gemmaManager.generateStreaming(
                                    prompt = prompt,
                                    systemPrompt = MODEL_CHAT_SYSTEM_PROMPT
                                ).collect { chunk ->
                                    if (chunk.isNotEmpty() && !firstTokenRecorded) {
                                        chatFirstTokenLatencyMs = SystemClock.elapsedRealtime() - startMs
                                        firstTokenRecorded = true
                                    }

                                    assistantText += chunk
                                    chatMessages = chatMessages.toMutableList().also { messages ->
                                        if (assistantIndex in messages.indices) {
                                            messages[assistantIndex] = ModelChatMessage(
                                                role = ChatRole.Assistant,
                                                text = assistantText
                                            )
                                        }
                                    }
                                }

                                chatTotalLatencyMs = SystemClock.elapsedRealtime() - startMs
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                // Handled cancellation gracefully, do not set an error message
                            } catch (e: Exception) {
                                chatErrorMessage = "Generation failed: ${e.message ?: "unknown error"}"
                            } finally {
                                chatGenerating = false
                                chatGeneratingJob = null
                            }
                        }
                        chatGeneratingJob = job
                    }
                )
                DashboardTab.MemoryBank -> MemoryBankPanel(
                    isFetchingFiles = isFetchingFiles,
                    availableFiles = availableFiles,
                    selectedFile = selectedFile,
                    dropdownExpanded = dropdownExpanded,
                    isFetchingRules = isFetchingRules,
                    rulesLoaded = rulesLoaded,
                    activeSectorId = activeSectorId,
                    errorMessage = errorMessage,
                    latestCoaching = latestCoaching,
                    downloadedFiles = downloadedMemoryBankFiles,
                    onDropdownExpandedChange = { dropdownExpanded = it },
                    onFileSelected = { file ->
                        selectedFile = file
                        dropdownExpanded = false
                        rulesLoaded = false
                    },
                    onRefreshFiles = {
                        isFetchingFiles = true
                        memoryBankFileStore.fetchAvailableFiles { files ->
                            scope.launch(Dispatchers.Main) {
                                isFetchingFiles = false
                                availableFiles = files
                                downloadedMemoryBankFiles =
                                    memoryBankFileStore.getDownloadedMemoryBankFiles()
                            }
                        }
                    },
                    onLoadRules = {
                        selectedFile?.let { file ->
                            isFetchingRules = true
                            errorMessage = null
                            try {
                                memoryBankFileStore.downloadMemoryBank(file) { savedFile ->
                                    scope.launch(Dispatchers.Main) {
                                        isFetchingRules = false
                                        rulesLoaded = savedFile != null
                                        downloadedMemoryBankFiles =
                                            memoryBankFileStore.getDownloadedMemoryBankFiles()
                                        selectedDownloadedMemoryBankFile =
                                            savedFile?.absolutePath
                                                ?: selectedDownloadedMemoryBankFile
                                                    ?.takeIf { it in downloadedMemoryBankFiles }
                                                ?: downloadedMemoryBankFiles.firstOrNull()
                                        if (savedFile == null) {
                                            errorMessage =
                                                "Failed to pull memory bank. Check if bucket is public."
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                isFetchingRules = false
                                rulesLoaded = false
                                errorMessage =
                                    "Failed to pull memory bank: ${e.message ?: "unknown error"}"
                            }
                        }
                    },
                    onShowJson = { filePath ->
                        try {
                            val file = File(filePath)
                            if (file.exists()) {
                                rawJson = file.readText()
                                showJsonDialog = true
                            } else {
                                errorMessage = "File not found: $filePath"
                            }
                        } catch (e: Exception) {
                            errorMessage = "Failed to read JSON: ${e.message ?: "unknown error"}"
                        }
                    },
                    onRemoveDownloadedFile = { filePath ->
                        if (memoryBankFileStore.removeDownloadedMemoryBankFile(filePath)) {
                            downloadedMemoryBankFiles =
                                memoryBankFileStore.getDownloadedMemoryBankFiles()
                            if (selectedDownloadedMemoryBankFile == filePath) {
                                selectedDownloadedMemoryBankFile = downloadedMemoryBankFiles.firstOrNull()
                            }
                            rulesLoaded = false
                        } else {
                            errorMessage = "Failed to remove downloaded file."
                        }
                    }
                )
                DashboardTab.DataLog -> DataLogPanel(
                    canReading = canReading,
                    canableStatus = canableStatus,
                    rawExportPath = rawExportPath,
                    rawHexOutput = rawHexOutput,
                    rawCanOutput = rawCanOutput,
                    canOutput = canOutput,
                    onToggleCanRead = toggleCanRead,
                    onToggleSimulator = toggleSimulator
                )
            }
        }
    }

    if (showJsonDialog && rawJson != null) {
        MemoryBankJsonDialog(
            rawJson = rawJson.orEmpty(),
            onDismiss = { showJsonDialog = false }
        )
    }
}
