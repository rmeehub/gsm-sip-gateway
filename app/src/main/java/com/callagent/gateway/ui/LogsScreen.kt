package com.callagent.gateway.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callagent.gateway.service.CallLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val logs by viewModel.callLogs.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Call History",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { viewModel.clearLogs() }) {
                Icon(Icons.Default.Delete, contentDescription = "Clear Logs", tint = MaterialTheme.colorScheme.error)
            }
        }

        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No call history", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(logs) { log ->
                    CallLogItem(log)
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
fun CallLogItem(log: CallLogEntry) {
    val isIncoming = log.direction == "IN"
    val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(log.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isIncoming) Icons.Default.CallReceived else Icons.Default.CallMade,
            contentDescription = null,
            tint = if (isIncoming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = log.number, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = dateStr, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "${log.durationSec}s", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        
        Surface(
            color = if (log.type == "GATEWAY") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = log.type,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (log.type == "GATEWAY") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
