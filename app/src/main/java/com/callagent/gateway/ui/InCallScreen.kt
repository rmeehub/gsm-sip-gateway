package com.callagent.gateway.ui

import android.telecom.Call
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callagent.gateway.gsm.GsmCallManager

@Composable
fun InCallScreen(viewModel: MainViewModel) {
    val state by viewModel.inCallState.collectAsState()
    var showDialpad by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = state.isActive,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(64.dp))
                
                Text(
                    text = state.number,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                val statusText = when (state.callState) {
                    Call.STATE_RINGING -> "Ringing..."
                    Call.STATE_DIALING -> "Calling..."
                    Call.STATE_ACTIVE -> "00:00" // Time can be added later
                    else -> "Connecting..."
                }
                
                Text(
                    text = if (state.isGatewayBridged) "SIP Bridged" else statusText,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.weight(1f))

                if (showDialpad) {
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
                                TextButton(onClick = { GsmCallManager.playDtmfTone(key[0]) }) {
                                    Text(key, fontSize = 28.sp, color = MaterialTheme.colorScheme.onBackground)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        InCallButton(
                            icon = Icons.Default.MicOff,
                            label = "Mute",
                            isActive = state.isMuted,
                            onClick = { viewModel.toggleMute() }
                        )
                        InCallButton(
                            icon = Icons.Default.Dialpad,
                            label = "Keypad",
                            isActive = showDialpad,
                            onClick = { showDialpad = !showDialpad }
                        )
                        InCallButton(
                            icon = Icons.Default.VolumeUp,
                            label = "Speaker",
                            isActive = state.isSpeaker,
                            onClick = { viewModel.toggleSpeaker() }
                        )
                    }
                    Spacer(modifier = Modifier.height(64.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (state.callState == Call.STATE_RINGING) {
                        FloatingActionButton(
                            onClick = { viewModel.answer() },
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "Answer", tint = Color.White)
                        }
                    }

                    FloatingActionButton(
                        onClick = { viewModel.hangup() },
                        containerColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "End Call", tint = Color.White)
                    }
                }
                
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }
}

@Composable
fun InCallButton(icon: ImageVector, label: String, isActive: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}
