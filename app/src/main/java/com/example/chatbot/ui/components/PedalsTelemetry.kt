package com.example.kotlin_chatbot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PedalsTelemetry(throttle: Double, brake: Double, modifier: Modifier = Modifier) {
    val throttleNormalized = if (throttle > 1.0) throttle / 100.0 else throttle
    val brakeNormalized = if (brake > 1.0) brake / 100.0 else brake
    
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
                    Text(text = "${(throttleNormalized * 100).toInt()}%", fontSize = 10.sp, color = NeonGreen, fontWeight = FontWeight.Black)
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { throttleNormalized.toFloat().coerceIn(0f, 1f) },
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
                    Text(text = "${(brakeNormalized * 100).toInt()}%", fontSize = 10.sp, color = NeonRed, fontWeight = FontWeight.Black)
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { brakeNormalized.toFloat().coerceIn(0f, 1f) },
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
                val gearText = when (gear) {
                    0 -> "N"
                    255, -1 -> "R"
                    else -> gear.toString()
                }
                Text(
                    text = gearText,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = if (gear == 0) NeonGreen else if (gear == 255 || gear == -1) NeonOrange else Color.White
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
