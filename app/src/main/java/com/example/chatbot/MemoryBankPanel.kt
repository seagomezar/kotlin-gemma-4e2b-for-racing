package com.example.kotlin_chatbot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatbot.models.CoachingPayload

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryBankPanel(
    isFetchingFiles: Boolean,
    availableFiles: List<String>,
    selectedFile: String?,
    dropdownExpanded: Boolean,
    isFetchingRules: Boolean,
    rulesLoaded: Boolean,
    activeSectorId: Int?,
    errorMessage: String?,
    latestCoaching: CoachingPayload?,
    downloadedFiles: List<String>,
    onDropdownExpandedChange: (Boolean) -> Unit,
    onFileSelected: (String) -> Unit,
    onRefreshFiles: () -> Unit,
    onLoadRules: () -> Unit,
    onShowJson: (String) -> Unit,
    onRemoveDownloadedFile: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isFetchingFiles) {
            CircularProgressIndicator(color = RacingRed)
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            MemoryFilePicker(
                availableFiles = availableFiles,
                selectedFile = selectedFile,
                dropdownExpanded = dropdownExpanded,
                onDropdownExpandedChange = onDropdownExpandedChange,
                onFileSelected = onFileSelected,
                onRefreshFiles = onRefreshFiles
            )
        }

        Button(
            onClick = onLoadRules,
            colors = ButtonDefaults.buttonColors(containerColor = CarbonGray),
            modifier = Modifier.padding(bottom = 16.dp),
            enabled = selectedFile != null
        ) {
            Text(if (isFetchingRules) "Downloading..." else "Pull Memory Bank", color = TrackWhite)
        }

        DownloadedMemoryBankList(
            downloadedFiles = downloadedFiles,
            onRemoveDownloadedFile = onRemoveDownloadedFile,
            onShowJson = onShowJson
        )

        if (downloadedFiles.isNotEmpty() || errorMessage != null) {
            MemoryBankStatus(
                rulesLoaded = rulesLoaded,
                activeSectorId = activeSectorId,
                isFetchingRules = isFetchingRules,
                errorMessage = errorMessage
            )
        }

        latestCoaching?.let {
            Spacer(modifier = Modifier.height(16.dp))
            CoachingCard(payload = it)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryFilePicker(
    availableFiles: List<String>,
    selectedFile: String?,
    dropdownExpanded: Boolean,
    onDropdownExpandedChange: (Boolean) -> Unit,
    onFileSelected: (String) -> Unit,
    onRefreshFiles: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(bottom = 16.dp)
            .fillMaxWidth(0.8f)
    ) {
        if (availableFiles.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = onDropdownExpandedChange,
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = selectedFile ?: "Select a file",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                        focusedBorderColor = RacingRed,
                        unfocusedBorderColor = CheckeredGray,
                        focusedTextColor = TrackWhite,
                        unfocusedTextColor = TrackWhite
                    ),
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { onDropdownExpandedChange(false) }
                ) {
                    availableFiles.forEach { file ->
                        DropdownMenuItem(
                            text = { Text(file) },
                            onClick = { onFileSelected(file) }
                        )
                    }
                }
            }
        } else {
            Text("No files found in bucket.", color = CheckeredGray, modifier = Modifier.weight(1f))
        }

        IconButton(onClick = onRefreshFiles) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh File List", tint = TrackWhite)
        }
    }
}

@Composable
private fun MemoryBankStatus(
    rulesLoaded: Boolean,
    activeSectorId: Int?,
    isFetchingRules: Boolean,
    errorMessage: String?
) {
    if (!isFetchingRules && errorMessage != null) {
        Text(
            text = errorMessage,
            color = RacingRed
        )
    }
}

@Composable
private fun DownloadedMemoryBankList(
    downloadedFiles: List<String>,
    onRemoveDownloadedFile: (String) -> Unit,
    onShowJson: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Text(
            text = "Downloaded files",
            color = TrackWhite,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (downloadedFiles.isEmpty()) {
            Text("No downloaded files yet.", color = CheckeredGray)
            return@Column
        }

        downloadedFiles.forEach { filePath ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = filePath.split(java.io.File.separatorChar).let { parts ->
                        if (parts.size >= 3) parts.takeLast(3).joinToString(java.io.File.separator) else filePath
                    },
                    color = CheckeredGray,
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp
                )
                IconButton(onClick = { onShowJson(filePath) }) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Show JSON",
                        tint = TrackWhite
                    )
                }
                IconButton(onClick = { onRemoveDownloadedFile(filePath) }) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Remove downloaded file",
                        tint = RacingRed
                    )
                }
            }
        }
    }
}

@Composable
fun MemoryBankJsonDialog(
    rawJson: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Memory Bank JSON") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(text = rawJson)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = RacingRed)
            }
        },
        containerColor = AsphaltBlack,
        titleContentColor = RacingRed,
        textContentColor = TrackWhite
    )
}
