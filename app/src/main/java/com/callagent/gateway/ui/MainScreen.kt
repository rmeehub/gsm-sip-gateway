package com.callagent.gateway.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun MainScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Call, contentDescription = "Dialer") },
                    label = { Text("Dialer") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = "Logs") },
                    label = { Text("Logs") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 }
                )
            }
        }
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (selectedTab) {
            0 -> DashboardScreen(viewModel, modifier)
            1 -> DialerScreen(modifier)
            2 -> LogsScreen(viewModel, modifier)
            3 -> SettingsScreen(viewModel, modifier)
        }
    }
}
