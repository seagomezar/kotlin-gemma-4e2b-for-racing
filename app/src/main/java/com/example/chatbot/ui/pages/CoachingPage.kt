package com.example.kotlin_chatbot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatbot.models.CoachingPayload

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachingPage(
    speed: Double,
    steering: Double,
    throttle: Double,
    brake: Double,
    sessionActive: Boolean,
    isThinking: Boolean,
    systemStatus: String,
    latestCoaching: CoachingPayload?,
    onToggleSession: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main Speedometer and Pedals HUD Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Speedometer Gauge
                CircularSpeedometerGauge(speed = speed)
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Compact Vertical Pedals telemetry replacing Gear/Lap box
                    CompactVerticalPedals(
                        throttle = throttle,
                        brake = brake,
                        modifier = Modifier.width(110.dp)
                    )
                    
                    // Pulse/Breathing Active badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                color = if (sessionActive) NeonGreen.copy(alpha = 0.15f) else CoolSteel.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (sessionActive) NeonGreen else CoolSteel,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = if (sessionActive) NeonGreen else CoolSteel,
                                    shape = RoundedCornerShape(3.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (sessionActive) "LIVE TELEMETRY" else "STANDBY",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }
        }

        // Steering column HUD card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SteeringHud(steering = steering)
            }
        }
        
        Button(
            onClick = onToggleSession,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (sessionActive) SurfaceDark else NeonRed,
                contentColor = Color.White
            ),
            border = BorderStroke(1.5.dp, if (sessionActive) NeonRed else Color.Transparent),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(
                text = if (sessionActive) "STOP COACHING SESSION" else "START COACHING SESSION",
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                letterSpacing = 1.sp
            )
        }

        // AI Coach status representation
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isThinking) {
                    GemmaThinkingOrb(modifier = Modifier.padding(vertical = 12.dp))
                }

                Text(
                    text = systemStatus,
                    color = CheckeredGray,
                    fontStyle = FontStyle.Italic,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        latestCoaching?.let { payload ->
            GlassmorphicCoachingCard(payload = payload)
        }
    }
}
