package com.example.kotlin_chatbot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatbot.core.CanableStatus
import com.example.chatbot.models.CoachingPayload
import com.example.chatbot.models.TelemetryPacket

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCoachPanel(
    sessionActive: Boolean,
    isThinking: Boolean,
    canReading: Boolean,
    canableStatus: CanableStatus,
    latestCoaching: CoachingPayload?,
    latestTelemetry: TelemetryPacket?,
    currentSectorId: Int?,
    selectedCoachType: CoachType,
    coachTypeDropdownExpanded: Boolean,
    downloadedMemoryBankFiles: List<String>,
    selectedMemoryBankFile: String?,
    memoryBankDropdownExpanded: Boolean,
    onCoachTypeDropdownExpandedChange: (Boolean) -> Unit,
    onCoachTypeSelected: (CoachType) -> Unit,
    onMemoryBankDropdownExpandedChange: (Boolean) -> Unit,
    onMemoryBankFileSelected: (String) -> Unit,
    onSessionToggle: () -> Unit,
    onTestSpeak: () -> Unit,
    onTestGemmaStructuredOutput: () -> Unit,
    onToggleCanRead: () -> Unit,
    onToggleSimulator: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AiCoachStatusCard(
            selectedCoachType = selectedCoachType,
            currentSectorId = currentSectorId,
            sessionActive = sessionActive,
            isThinking = isThinking,
            modifier = Modifier
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = CarbonGray),
            border = BorderStroke(1.dp, RacingRed.copy(alpha = 0.45f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                ExposedDropdownMenuBox(
                    expanded = coachTypeDropdownExpanded,
                    onExpandedChange = onCoachTypeDropdownExpandedChange,
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedCoachType.label,
                        onValueChange = {},
                        readOnly = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = RacingCyan
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = coachTypeDropdownExpanded)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                            focusedBorderColor = RacingCyan,
                            unfocusedBorderColor = RacingRed.copy(alpha = 0.55f),
                            focusedTextColor = TrackWhite,
                            unfocusedTextColor = TrackWhite
                        ),
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = coachTypeDropdownExpanded,
                        onDismissRequest = { onCoachTypeDropdownExpandedChange(false) }
                    ) {
                        CoachType.values().forEach { coachType ->
                            DropdownMenuItem(
                                text = { Text(coachType.label) },
                                onClick = { onCoachTypeSelected(coachType) }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = memoryBankDropdownExpanded,
                    onExpandedChange = onMemoryBankDropdownExpandedChange,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedMemoryBankFile?.toDisplayFileName()
                            ?: if (downloadedMemoryBankFiles.isEmpty()) {
                                "No downloaded memory bank files"
                            } else {
                                "Select downloaded memory bank"
                            },
                        onValueChange = {},
                        readOnly = true,
                        enabled = downloadedMemoryBankFiles.isNotEmpty(),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = RacingRed
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = memoryBankDropdownExpanded)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                            focusedBorderColor = RacingCyan,
                            unfocusedBorderColor = CheckeredGray.copy(alpha = 0.6f),
                            focusedTextColor = TrackWhite,
                            unfocusedTextColor = TrackWhite,
                            disabledTextColor = CheckeredGray,
                            disabledBorderColor = CarbonGray
                        ),
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = memoryBankDropdownExpanded,
                        onDismissRequest = { onMemoryBankDropdownExpandedChange(false) }
                    ) {
                        downloadedMemoryBankFiles.forEach { filePath ->
                            DropdownMenuItem(
                                text = { Text(filePath.toDisplayFileName()) },
                                onClick = { onMemoryBankFileSelected(filePath) }
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .padding(bottom = 10.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val usbActive = canReading && canableStatus != CanableStatus.SIMULATING
            Button(
                onClick = onToggleCanRead,
                enabled = !canReading || canableStatus != CanableStatus.SIMULATING,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (usbActive) CarbonGray else RacingRed,
                    contentColor = if (usbActive) TrackWhite else AsphaltBlack
                ),
                modifier = Modifier
                    .weight(1f)
            ) {
                Icon(
                    imageVector = if (usbActive) Icons.Default.Stop else Icons.Default.SettingsInputComponent,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (usbActive) "STOP USB" else "START USB",
                    fontWeight = FontWeight.Bold
                )
            }

            val simulatorActive = canReading && canableStatus == CanableStatus.SIMULATING
            Button(
                onClick = onToggleSimulator,
                enabled = !canReading || canableStatus == CanableStatus.SIMULATING,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (simulatorActive) CarbonGray else RacingRed,
                    contentColor = if (simulatorActive) TrackWhite else AsphaltBlack
                ),
                modifier = Modifier
                    .weight(1f)
            ) {
                Icon(
                    imageVector = if (simulatorActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (simulatorActive) "STOP SIM" else "START SIM",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        CoachingSessionButton(
            sessionActive = sessionActive,
            onToggle = onSessionToggle
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onTestSpeak,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RacingCyan,
                    contentColor = AsphaltBlack
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("TEST SPEAK", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onTestGemmaStructuredOutput,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RacingCyan,
                    contentColor = AsphaltBlack
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Science, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("TEST JSON", fontWeight = FontWeight.Bold)
            }
        }

        Text(
            text = when {
                isThinking -> "Generating coaching..."
                sessionActive -> "AI Coach is listening."
                else -> "Start a coaching session to analyze incoming data."
            },
            color = if (sessionActive || isThinking) RacingRed else CheckeredGray,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(16.dp))
        TelemetryIndicators(packet = latestTelemetry)

        latestCoaching?.let {
            Spacer(modifier = Modifier.height(16.dp))
            CoachingCard(payload = it)
        }
    }
}

@Composable
private fun AiCoachStatusCard(
    selectedCoachType: CoachType,
    currentSectorId: Int?,
    sessionActive: Boolean,
    isThinking: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = AsphaltBlack),
        border = BorderStroke(1.dp, RacingCyan.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AI COACH",
                        color = RacingRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = selectedCoachType.label,
                        color = TrackWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "SECTOR",
                        color = CheckeredGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = currentSectorId?.toString() ?: "--",
                        color = if (currentSectorId != null) RacingCyan else CheckeredGray,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            StatusPill(
                text = when {
                    isThinking -> "ANALYZING"
                    sessionActive -> "LIVE"
                    else -> "STANDBY"
                },
                color = when {
                    isThinking -> RacingCyan
                    sessionActive -> RacingRed
                    else -> CheckeredGray
                }
            )
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.65f))
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun TelemetryIndicators(packet: TelemetryPacket?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TelemetryIndicatorCard(
                label = "Speed",
                value = packet?.speed?.let { "%.1f mph".format(it) } ?: "-- mph",
                detail = "Gear ${packet?.gear?.toString() ?: "-"}",
                progress = ((packet?.speed ?: 0.0) / 180.0).toFloat(),
                accent = RacingRed,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp, bottom = 8.dp)
            )
            TelemetryIndicatorCard(
                label = "RPM",
                value = packet?.rpm?.let { "%.0f".format(it) } ?: "--",
                detail = if ((packet?.rpm ?: 0.0) > 6500.0) "Shift soon" else "Engine load",
                progress = ((packet?.rpm ?: 0.0) / 8000.0).toFloat(),
                accent = if ((packet?.rpm ?: 0.0) > 6500.0) RacingRed else TrackWhite,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp, bottom = 8.dp)
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            val brakePressure = packet?.brakePressurePsi ?: packet?.brake ?: 0.0
            TelemetryIndicatorCard(
                label = "Brake",
                value = if (packet?.brakeSwitchApplied == true) "ON" else "%.0f psi".format(brakePressure),
                detail = if (packet?.brakeSwitchApplied == true) "Switch active" else "Pressure",
                progress = (brakePressure / 1500.0).toFloat(),
                accent = if (packet?.brakeSwitchApplied == true || brakePressure > 0.0) RacingRed else CheckeredGray,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp, bottom = 8.dp)
            )
            val throttle = packet?.ecuDbwApp1Percent ?: packet?.pedalPositionPercent ?: packet?.throttle ?: 0.0
            TelemetryIndicatorCard(
                label = "DBW APP1",
                value = "%.0f%%".format(throttle),
                detail = "Pedal position",
                progress = (throttle / 100.0).toFloat(),
                accent = TrackWhite,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp, bottom = 8.dp)
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            val lateralG = packet?.lateralAccelG ?: 0.0
            val inlineG = packet?.inlineAccelG ?: 0.0
            val gLoad = kotlin.math.max(kotlin.math.abs(lateralG), kotlin.math.abs(inlineG))
            TelemetryIndicatorCard(
                label = "G-Load",
                value = "Lat %.2f".format(lateralG),
                detail = "Long %.2f".format(inlineG),
                progress = (gLoad / 2.0).toFloat(),
                accent = if (gLoad > 1.0) RacingRed else TrackWhite,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            )
            val wheelDelta = packet?.wheelSpeedDeltas?.maxOfOrNull { kotlin.math.abs(it) }
                ?: listOfNotNull(
                    packet?.wheelSpeedFlMph,
                    packet?.wheelSpeedFrMph,
                    packet?.wheelSpeedRlMph,
                    packet?.wheelSpeedRrMph
                ).let { speeds ->
                    if (speeds.size >= 2) speeds.maxOrNull().orZero() - speeds.minOrNull().orZero() else 0.0
                }
            TelemetryIndicatorCard(
                label = "Wheel Delta",
                value = "%.1f mph".format(wheelDelta),
                detail = if (wheelDelta > 5.0) "Check grip" else "Balanced",
                progress = (wheelDelta / 20.0).toFloat(),
                accent = if (wheelDelta > 5.0) RacingRed else CheckeredGray,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun TelemetryIndicatorCard(
    label: String,
    value: String,
    detail: String,
    progress: Float,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CarbonGray)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label.uppercase(),
                color = CheckeredGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                color = TrackWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = detail,
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = accent,
                trackColor = AsphaltBlack
            )
        }
    }
}

private fun String.toDisplayFileName(): String {
    val parts = split(java.io.File.separatorChar)
    return if (parts.size >= 3) parts.takeLast(3).joinToString(java.io.File.separator) else this
}

private fun Double?.orZero(): Double = this ?: 0.0
