package com.example.kotlin_chatbot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun CompactVerticalPedals(throttle: Double, brake: Double, modifier: Modifier = Modifier) {
    val throttleNormalized = (if (throttle > 1.0) throttle / 100.0 else throttle).coerceIn(0.0, 1.0).toFloat()
    val brakeNormalized = (if (brake > 1.0) brake / 100.0 else brake).coerceIn(0.0, 1.0).toFloat()
    
    Row(
        modifier = modifier.height(100.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Brake Vertical Bar
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "BRK",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
            // Vertical bar track
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(60.dp)
                    .background(SurfaceBorder, shape = RoundedCornerShape(3.dp))
                    .clip(RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(brakeNormalized)
                        .background(NeonRed)
                )
            }
            Text(
                text = "${(brakeNormalized * 100).toInt()}%",
                fontSize = 9.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                color = NeonRed
            )
        }
        
        // Throttle Vertical Bar
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "THR",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
            // Vertical bar track
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(60.dp)
                    .background(SurfaceBorder, shape = RoundedCornerShape(3.dp))
                    .clip(RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(throttleNormalized)
                        .background(NeonGreen)
                )
            }
            Text(
                text = "${(throttleNormalized * 100).toInt()}%",
                fontSize = 9.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                color = NeonGreen
            )
        }
    }
}
