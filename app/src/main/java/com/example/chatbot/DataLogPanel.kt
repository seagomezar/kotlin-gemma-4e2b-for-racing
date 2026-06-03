package com.example.kotlin_chatbot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatbot.core.CanableStatus

@Composable
fun DataLogPanel(
    canReading: Boolean,
    canableStatus: CanableStatus,
    rawExportPath: String,
    rawHexOutput: String,
    rawCanOutput: String,
    canOutput: String,
    onToggleCanRead: () -> Unit,
    onToggleSimulator: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Button(
                onClick = onToggleCanRead,
                enabled = !canReading || canableStatus != CanableStatus.SIMULATING,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canReading && canableStatus != CanableStatus.SIMULATING) CarbonGray else RacingRed
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Text(
                    if (canReading && canableStatus != CanableStatus.SIMULATING) "STOP USB" else "START USB",
                    color = TrackWhite,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = onToggleSimulator,
                enabled = !canReading || canableStatus == CanableStatus.SIMULATING,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canReading && canableStatus == CanableStatus.SIMULATING) CarbonGray else RacingRed
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            ) {
                Text(
                    if (canReading && canableStatus == CanableStatus.SIMULATING) "STOP SIMULATOR" else "START SIMULATOR",
                    color = TrackWhite,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = AsphaltBlack)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "CANable: $canableStatus",
                    color = RacingRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "FILE: $rawExportPath",
                    color = CheckeredGray,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "USB HEX: $rawHexOutput",
                    color = TrackWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "RAW: $rawCanOutput",
                    color = TrackWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = canOutput,
                    color = TrackWhite,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 320.dp)
                )
            }
        }
    }
}
