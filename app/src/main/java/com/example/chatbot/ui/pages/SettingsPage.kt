package com.example.kotlin_chatbot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    coachingEngine: String,
    onEngineChange: (String) -> Unit,
    selectedFile: String?,
    dropdownExpanded: Boolean,
    onDropdownExpandedChange: (Boolean) -> Unit,
    availableFiles: List<String>,
    isFetchingFiles: Boolean,
    isFetchingRules: Boolean,
    rulesLoaded: Boolean,
    activeSectorId: Int?,
    errorMessage: String?,
    onFileSelected: (String) -> Unit,
    onLoadRules: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Coaching System Engine Selector Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "COACHING ENGINE CONFIGURATION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonRed,
                    letterSpacing = 1.sp
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Gemma Engine Select Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                width = 1.5.dp,
                                color = if (coachingEngine == "gemma") PurpleAura else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (coachingEngine == "gemma") PurpleAura.copy(alpha = 0.1f) else Color.Black
                        ),
                        onClick = { onEngineChange("gemma") }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.SmartToy, contentDescription = "Gemma AI", tint = if (coachingEngine == "gemma") PurpleAura else Color.Gray)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("On-Device AI", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Gemma 4:E2B Edge", color = Color.Gray, fontSize = 10.sp)
                        }
                    }
                    
                    // Memory Bank Engine Select Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                width = 1.5.dp,
                                color = if (coachingEngine == "memory_bank") NeonCyan else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (coachingEngine == "memory_bank") NeonCyan.copy(alpha = 0.1f) else Color.Black
                        ),
                        onClick = { onEngineChange("memory_bank") }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = "Memory Bank", tint = if (coachingEngine == "memory_bank") NeonCyan else Color.Gray)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Memory Bank", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Rule-Based SaaS", color = Color.Gray, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // Memory Bank Selector & Load Panel Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "MEMORY BANK SELECTOR",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    letterSpacing = 1.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                
                if (isFetchingFiles) {
                    CircularProgressIndicator(color = NeonRed)
                } else if (availableFiles.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = onDropdownExpandedChange,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedFile ?: "Select a file",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                                focusedBorderColor = NeonRed,
                                unfocusedBorderColor = SurfaceBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { onDropdownExpandedChange(false) }
                        ) {
                            availableFiles.forEach { file ->
                                DropdownMenuItem(
                                    text = { Text(file) },
                                    onClick = {
                                        onFileSelected(file)
                                        onDropdownExpandedChange(false)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Text("No files found in bucket.", color = CheckeredGray)
                }

                Button(
                    onClick = onLoadRules,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    border = BorderStroke(1.dp, SurfaceBorder),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedFile != null
                ) {
                    Text(if (isFetchingRules) "FETCHING RULES..." else "LOAD MEMORY BANK", color = Color.White, fontWeight = FontWeight.Bold)
                }

                if (rulesLoaded) {
                    val statusText = if (activeSectorId != null) {
                        "Monitoring Sector $activeSectorId"
                    } else {
                        "Waiting for Track Coordinates..."
                    }
                    Text(
                        text = statusText,
                        color = NeonGreen,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                } else if (!isFetchingRules) {
                    Text(errorMessage ?: "No Rules Loaded. Tap Load.", color = if (errorMessage != null) NeonRed else CheckeredGray)
                }
            }
        }
        
        // System Information/Credits Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("SYSTEM INFORMATION", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                Text("Version: 2.1.0-Pro", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Platform: Sonoma Apex Core v2.1", color = Color.LightGray, fontSize = 11.sp)
                Text("Developer: ApexAI Team", color = Color.Gray, fontSize = 10.sp)
            }
        }
    }
}
