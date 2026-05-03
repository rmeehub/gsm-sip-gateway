package com.callagent.gateway.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callagent.gateway.web.WebServer
import java.net.NetworkInterface

@Composable
fun SettingsScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("gateway", Context.MODE_PRIVATE) }
    
    var server by remember { mutableStateOf(prefs.getString("server", "sip.callagent.pro") ?: "") }
    var port by remember { mutableStateOf(prefs.getInt("port", 5060).toString()) }
    var user by remember { mutableStateOf(prefs.getString("user", "") ?: "") }
    var pass by remember { mutableStateOf(prefs.getString("pass", "") ?: "") }
    var localServer by remember { mutableStateOf(prefs.getBoolean("local_server", false)) }
    var usePublicIp by remember { mutableStateOf(prefs.getBoolean("use_public_ip", false)) }
    var autoconnect by remember { mutableStateOf(prefs.getBoolean("autoconnect", true)) }
    var enableWebServer by remember { mutableStateOf(prefs.getBoolean("enable_web_server", true)) }
    var configUrl by remember { mutableStateOf("") }
    
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

        if (!localServer) {
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

            OutlinedTextField(
                value = configUrl,
                onValueChange = { configUrl = it },
                label = { Text("Config URL (Import details)") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = { viewModel.importConfigFromUrl(configUrl) }) {
                        Text("IMPORT")
                    }
                }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(checked = autoconnect, onCheckedChange = { autoconnect = it })
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Auto-connect", fontWeight = FontWeight.SemiBold)
                Text("Start gateway automatically on app launch", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(checked = usePublicIp, onCheckedChange = { usePublicIp = it })
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Public IP Mode (STUN)", fontWeight = FontWeight.SemiBold)
                Text("Use STUN to discover public IP for NAT traversal", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Web Server Control - Independent of client/server mode
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(checked = enableWebServer, onCheckedChange = { 
                enableWebServer = it
                if (it) {
                    WebServer.start(context, 8080)
                } else {
                    WebServer.stop()
                }
            })
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Web Management Interface", fontWeight = FontWeight.SemiBold)
                Text("Enable web UI for log monitoring and configuration", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        
        if (enableWebServer) {
            Spacer(modifier = Modifier.height(8.dp))
            val localIp = remember { getLocalIpAddress() }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Web Interface URL", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(
                            text = if (localIp != null) "http://$localIp:8080" else "Starting...",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
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
                    .putBoolean("use_public_ip", usePublicIp)
                    .putBoolean("autoconnect", autoconnect)
                    .putBoolean("enable_web_server", enableWebServer)
                    .apply()
                
                // Start/stop web server based on setting
                if (enableWebServer) {
                    WebServer.start(context, 8080)
                } else {
                    WebServer.stop()
                }
                
                viewModel.toggleGateway(
                    server = server,
                    port = port.toIntOrNull() ?: 5060,
                    user = user,
                    pass = pass,
                    localServer = localServer,
                    usePublicIp = usePublicIp
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

private fun getLocalIpAddress(): String? {
    try {
        val en = NetworkInterface.getNetworkInterfaces()
        while (en.hasMoreElements()) {
            val intf = en.nextElement()
            val enumIpAddr = intf.inetAddresses
            while (enumIpAddr.hasMoreElements()) {
                val inetAddress = enumIpAddr.nextElement()
                if (!inetAddress.isLoopbackAddress && inetAddress is java.net.Inet4Address) {
                    return inetAddress.hostAddress
                }
            }
        }
    } catch (ex: Exception) {
        ex.printStackTrace()
    }
    return null
}
