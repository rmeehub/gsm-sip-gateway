package com.callagent.gateway

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.callagent.gateway.service.GatewayService
import com.callagent.gateway.ui.GatewayTheme
import com.callagent.gateway.ui.InCallScreen
import com.callagent.gateway.ui.MainScreen
import com.callagent.gateway.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request root permission first - critical for gateway operation
        RootShell.init()
        
        // Show prevalidation details in logs
        logPreValidation()
        
        requestPermissions()
        requestBatteryOptimizationExemption()
        requestDefaultDialerRole()
        
        autoStartGateway()

        setContent {
            GatewayTheme {
                MainScreen(viewModel)
                InCallScreen(viewModel)
            }
        }
    }

    private fun logPreValidation() {
        val prefs = getSharedPreferences("gateway", MODE_PRIVATE)
        val server = prefs.getString("server", "") ?: ""
        val port = prefs.getInt("port", 5060)
        val user = prefs.getString("user", "") ?: ""
        val localServer = prefs.getBoolean("local_server", false)
        val usePublicIp = prefs.getBoolean("use_public_ip", false)
        val autoconnect = prefs.getBoolean("autoconnect", true)
        
        Log.i("GatewayApp", "=== CONFIGURATION PREVALIDATION ===")
        Log.i("GatewayApp", "Mode: ${if (localServer) "SERVER (PBX)" else "CLIENT"}")
        Log.i("GatewayApp", "Server: ${if (server.isNotEmpty()) server else "N/A"}")
        Log.i("GatewayApp", "Port: $port")
        Log.i("GatewayApp", "User: ${if (user.isNotEmpty()) user else "N/A"}")
        Log.i("GatewayApp", "Auto-connect: $autoconnect")
        Log.i("GatewayApp", "Public IP Mode: $usePublicIp")
        Log.i("GatewayApp", "Root Shell: ${if (RootShell.isAlive) "ACTIVE" else "INACTIVE"}")
        Log.i("GatewayApp", "====================================")
    }

    private fun autoStartGateway() {
        val prefs = getSharedPreferences("gateway", MODE_PRIVATE)
        if (!prefs.getBoolean("autoconnect", true)) return
        val server = prefs.getString("server", "") ?: ""
        val user = prefs.getString("user", "") ?: ""
        if (server.isEmpty() || user.isEmpty()) return
        val port = prefs.getInt("port", 5060)
        val pass = prefs.getString("pass", "") ?: ""
        val localServer = prefs.getBoolean("local_server", false)
        val usePublicIp = prefs.getBoolean("use_public_ip", false)
        
        viewModel.toggleGateway(server, port, user, pass, localServer, usePublicIp)
    }

    // ── Permissions ─────────────────────────────────────

    private val REQ_PERMS = 101
    private val REQ_DEFAULT_DIALER = 102

    private fun requestPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            perms.add(Manifest.permission.READ_PHONE_NUMBERS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_PERMS)
        }
    }

    private fun requestDefaultDialerRole() {
        val tm = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        if (packageName == tm.defaultDialerPackage) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (rm.isRoleAvailable(RoleManager.ROLE_DIALER) &&
                !rm.isRoleHeld(RoleManager.ROLE_DIALER)
            ) {
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER), REQ_DEFAULT_DIALER)
            }
        } else {
            @Suppress("DEPRECATION")
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            }
            startActivityForResult(intent, REQ_DEFAULT_DIALER)
        }
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_DEFAULT_DIALER) {
            // Dialer role result
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Handle permission results if needed
    }
}
