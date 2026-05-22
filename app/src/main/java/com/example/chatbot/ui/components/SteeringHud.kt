package com.example.kotlin_chatbot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SteeringHud(steering: Double, modifier: Modifier = Modifier) {
    val isDegrees = kotlin.math.abs(steering) > 2.0
    val steeringAngle = if (isDegrees) steering else steering * 180.0
    val steeringNormalized = (if (isDegrees) steering / 90.0 else steering).coerceIn(-1.0, 1.0).toFloat()
    
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
                text = "%.2f°".format(steeringAngle),
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = if (kotlin.math.abs(steeringNormalized) > 0.5f) NeonOrange else Color.White
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
            val barWidth = (steeringNormalized * (size.width / 2f))
            
            drawRect(
                color = if (kotlin.math.abs(steeringNormalized) > 0.6f) NeonOrange else NeonCyan,
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
