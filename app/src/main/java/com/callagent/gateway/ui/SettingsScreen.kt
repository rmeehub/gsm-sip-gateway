package com.callagent.gateway.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("gateway", Context.MODE_PRIVATE) }
    
    var server by remember { mutableStateOf(prefs.getString("server", "sip.callagent.pro") ?: "") }
    var port by remember { mutableStateOf(prefs.getInt("port", 5060).toString()) }
    var user by remember { mutableStateOf(prefs.getString("user", "") ?: "") }
    var pass by remember { mutableStateOf(prefs.getString("pass", "") ?: "") }
    var localServer by remember { mutableStateOf(prefs.getBoolean("local_server", false)) }
    
    val state by viewModel.gatewayState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            label = { Text("SIP Server IP / Domain") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter { char -> char.isDigit() } },
            label = { Text("Port (Default 5060)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(checked = localServer, onCheckedChange = { localServer = it })
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Local SIP PBX Mode", fontWeight = FontWeight.SemiBold)
                Text("Run internal SIP Server instead of connecting to Asterisk", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                prefs.edit()
                    .putString("server", server)
                    .putInt("port", port.toIntOrNull() ?: 5060)
                    .putString("user", user)
                    .putString("pass", pass)
                    .putBoolean("local_server", localServer)
                    .apply()
                
                viewModel.toggleGateway(
                    server = server,
                    port = port.toIntOrNull() ?: 5060,
                    user = user,
                    pass = pass,
                    localServer = localServer
                )
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(if (state.isRunning) "STOP GATEWAY" else "START GATEWAY", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
