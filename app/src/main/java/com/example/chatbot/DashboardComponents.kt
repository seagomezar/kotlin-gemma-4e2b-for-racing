package com.example.kotlin_chatbot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatbot.models.CoachingPayload

@Composable
fun DashboardBottomBar(
    selectedTab: DashboardTab,
    onTabSelected: (DashboardTab) -> Unit
) {
    NavigationBar(containerColor = AsphaltBlack) {
        DashboardTab.values().forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = CheckeredGray,
                    selectedTextColor = TrackWhite,
                    unselectedTextColor = CheckeredGray,
                    indicatorColor = CarbonGray
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RacingHeader() {
    CenterAlignedTopAppBar(
        windowInsets = WindowInsets(0.dp),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = AsphaltBlack,
            titleContentColor = MaterialTheme.colorScheme.primary
        ),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DirectionsCar,
                    contentDescription = "Gemma Racing",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "APEX AI COACH",
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    )
}

@Composable
fun TelemetryHud(speed: Double, steering: Double) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = CarbonGray)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.Speed, contentDescription = "Speed", tint = TrackWhite)
                    Text(text = "SPEED", fontSize = 12.sp, color = CheckeredGray)
                    Text(text = "%.1f mph".format(speed), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = RacingRed)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "STEERING", fontSize = 12.sp, color = CheckeredGray)
                    Text(text = "%.2f°".format(steering), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TrackWhite)
                }
            }
        }
    }
}

@Composable
fun CoachingSessionButton(
    sessionActive: Boolean,
    onToggle: () -> Unit
) {
    Button(
        onClick = onToggle,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (sessionActive) CarbonGray else RacingRed,
            contentColor = if (sessionActive) TrackWhite else AsphaltBlack
        ),
        modifier = Modifier
            .padding(bottom = 10.dp)
            .fillMaxWidth()
    ) {
        Text(
            if (sessionActive) "STOP COACHING SESSION" else "START COACHING SESSION",
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CoachingCard(payload: CoachingPayload) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = AsphaltBlack),
        border = BorderStroke(
            1.dp,
            if (payload.urgency == "HIGH") RacingRed else RacingCyan.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = payload.targetCorner.uppercase(),
                color = if (payload.urgency == "HIGH") RacingRed else RacingCyan,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = payload.instruction,
                color = TrackWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                textAlign = TextAlign.Center
            )
            if (payload.latencyMs > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Latency: ${payload.latencyMs}ms",
                    color = CheckeredGray,
                    fontSize = 12.sp
                )
            }
        }
    }
}
