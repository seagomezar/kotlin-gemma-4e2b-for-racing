package com.example.kotlin_chatbot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatbot.models.CoachingPayload

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
