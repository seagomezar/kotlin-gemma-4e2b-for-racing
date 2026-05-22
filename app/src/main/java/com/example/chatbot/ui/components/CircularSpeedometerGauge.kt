package com.example.kotlin_chatbot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
