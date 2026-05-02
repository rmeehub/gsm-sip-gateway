package com.callagent.gateway.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
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

@Composable
fun DashboardScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.gatewayState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Gateway Status",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (state.isRunning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (state.isRunning) Icons.Default.PlayArrow else Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = if (state.isRunning) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = state.status,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = if (state.isRunning) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = state.info,
                    fontSize = 16.sp,
                    color = if (state.isRunning) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCard("Incoming", state.incomingCalls.toString())
            StatCard("Outgoing", state.outgoingCalls.toString())
        }
    }
}

@Composable
fun StatCard(title: String, value: String) {
    Card(
        modifier = Modifier.size(120.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = title, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}
