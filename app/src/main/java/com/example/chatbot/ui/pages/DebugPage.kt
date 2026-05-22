package com.example.kotlin_chatbot

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.example.chatbot.models.TelemetryPacket
import com.google.gson.Gson
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun exportTelemetry(context: Context, packets: List<TelemetryPacket>) {
    if (packets.isEmpty()) return
    try {
        val jsonString = Gson().toJson(packets)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "telemetry_rec_$timestamp.json"
        
        val dir = context.getExternalFilesDir("recordings")
        if (dir != null && !dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, fileName)
        file.writeText(jsonString)
        
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, jsonString)
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "Apex Telemetry Log - $fileName")
        }
        
        val shareIntent = Intent.createChooser(sendIntent, "Export Telemetry Packet Data")
        context.startActivity(shareIntent)
        
        Toast.makeText(context, "Telemetry saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun DebugPage(
    speed: Double,
    steering: Double,
    throttle: Double,
    brake: Double,
    gear: Int,
    lap: Int,
    packetCount: Long,
    sessionActive: Boolean,
    coachingEngine: String,
    shockPots: List<Double>,
    tireSlip: List<Double>,
    wheelDeltas: List<Double>,
    systemStatus: String,
    isRecording: Boolean,
    onRecordingChange: (Boolean) -> Unit,
    recordedPackets: List<TelemetryPacket>,
    onClearRecording: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Telemetry Log Recorder Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TELEMETRY LOG RECORDER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonRed,
                        letterSpacing = 1.sp
                    )
                    
                    // Recording Status Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                color = if (isRecording) NeonRed.copy(alpha = 0.15f) 
                                        else if (recordedPackets.isNotEmpty()) NeonOrange.copy(alpha = 0.15f) 
                                        else CoolSteel.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .border(
                                width = 0.5.dp,
                                color = if (isRecording) NeonRed 
                                        else if (recordedPackets.isNotEmpty()) NeonOrange 
                                        else CoolSteel.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse"
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .graphicsLayer(alpha = if (isRecording) alpha else 1.0f)
                                .background(
                                    color = if (isRecording) NeonRed 
                                            else if (recordedPackets.isNotEmpty()) NeonOrange 
                                            else CoolSteel,
                                    shape = RoundedCornerShape(3.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRecording) "RECORDING" 
                                   else if (recordedPackets.isNotEmpty()) "PAUSED (${recordedPackets.size})" 
                                   else "READY",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                
                // Readout Stats
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Recorded Packets", color = Color.Gray, fontSize = 12.sp)
                        Text("${recordedPackets.size} frames", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Active Buffer Size", color = Color.Gray, fontSize = 12.sp)
                        val sizeKb = (recordedPackets.size * 0.25).coerceAtLeast(0.0) // approx 0.25 KB per packet
                        Text("%.2f KB".format(sizeKb), color = Color.LightGray, fontSize = 12.sp)
                    }

                    if (recordedPackets.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Log Target: /sdcard/Android/data/.../files/recordings/",
                            color = CheckeredGray,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Start/Stop Toggle
                    Button(
                        onClick = { onRecordingChange(!isRecording) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) Color.Transparent else NeonRed,
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, if (isRecording) NeonRed else Color.Transparent),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1.2f).height(36.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = if (isRecording) "STOP REC" else "START RECORDING",
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                    
                    // Export
                    Button(
                        onClick = {
                            exportTelemetry(context, recordedPackets)
                        },
                        enabled = recordedPackets.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceBorder,
                            contentColor = Color.White,
                            disabledContainerColor = SurfaceBorder.copy(alpha = 0.4f),
                            disabledContentColor = Color.Gray
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1.2f).height(36.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "EXPORT JSON",
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                    
                    // Clear
                    if (recordedPackets.isNotEmpty()) {
                        IconButton(
                            onClick = onClearRecording,
                            modifier = Modifier
                                .size(36.dp)
                                .background(SurfaceBorder.copy(alpha = 0.5f), shape = RoundedCornerShape(6.dp))
                                .border(0.5.dp, SurfaceBorder, shape = RoundedCornerShape(6.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear Recording",
                                tint = NeonRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Advanced Pro Sensor Telemetry Grid
        ProSensorTelemetryGrid(
            shockPots = shockPots,
            tireSlip = tireSlip,
            wheelDeltas = wheelDeltas
        )

        // Diagnostic Connection & Packets Counter Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "CONNECTION DIAGNOSTICS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    letterSpacing = 1.sp
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("WebSocket Status", color = Color.Gray, fontSize = 12.sp)
                    Text(
                        text = if (sessionActive) "ACTIVE CONNECTED" else "LISTEN READY",
                        color = if (sessionActive) NeonGreen else NeonCyan,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("SSE Endpoint URL", color = Color.Gray, fontSize = 12.sp)
                    Text("apexai-run.app/events/telemetry", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Received Packets", color = Color.Gray, fontSize = 12.sp)
                    Text("$packetCount", color = NeonCyan, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Active Engine", color = Color.Gray, fontSize = 12.sp)
                    Text(
                        text = if (coachingEngine == "gemma") "ON-DEVICE GEMMA AI" else "RULE-BASED MEMORY BANK",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Model & Engine Latency Stats Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "MODEL LATENCY & INFERENCE STATUS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PurpleAura,
                    letterSpacing = 1.sp
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("LLM Loader Binary", color = Color.Gray, fontSize = 12.sp)
                    Text("gemma-4-E2B-it.litertlm", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("System Message", color = Color.Gray, fontSize = 12.sp)
                    Text(systemStatus, color = Color.LightGray, fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.End)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Apex Steering Threshold", color = Color.Gray, fontSize = 12.sp)
                    Text("± 0.50 Var Limit", color = NeonOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // Raw Telemetry Stream Visualizer Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "RAW CHANNELS CONSOLE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.LightGray,
                    letterSpacing = 1.sp
                )
                
                Text(
                    text = "SPD: %.1f km/h  |  STR: %.2f°  |  THR: %.0f%%  |  BRK: %.0f%%  |  GR: %d  |  LAP: %d"
                        .format(speed, steering, throttle, brake, gear, lap),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.LightGray
                )
            }
        }
    }
}
