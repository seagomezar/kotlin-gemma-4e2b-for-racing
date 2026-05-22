package com.example.kotlin_chatbot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
