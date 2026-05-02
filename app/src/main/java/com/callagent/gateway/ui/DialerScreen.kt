package com.callagent.gateway.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callagent.gateway.gsm.GsmCallManager

@Composable
fun DialerScreen(modifier: Modifier = Modifier) {
    var number by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            text = if (number.isEmpty()) "Enter number" else number,
            fontSize = 36.sp,
            fontWeight = FontWeight.Light,
            color = if (number.isEmpty()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("*", "0", "#")
        )

        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { key ->
                    DialpadButton(text = key, onClick = { number += key })
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))
            FloatingActionButton(
                onClick = { 
                    if (number.isNotEmpty()) {
                        GsmCallManager.makeCall(context, number)
                    }
                },
                containerColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(72.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.Call, contentDescription = "Call", modifier = Modifier.size(36.dp), tint = Color.White)
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (number.isNotEmpty()) {
                    TextButton(onClick = { number = number.dropLast(1) }) {
                        Text("Delete", fontSize = 16.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun DialpadButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontSize = 32.sp, fontWeight = FontWeight.Normal)
    }
}
