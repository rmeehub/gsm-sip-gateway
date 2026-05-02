package com.callagent.gateway.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telecom.Call
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.callagent.gateway.gsm.GsmCallManager
import com.callagent.gateway.service.CallLogEntry
import com.callagent.gateway.service.CallLogStore
import com.callagent.gateway.service.GatewayService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GatewayState(
    val status: String = "IDLE",
    val info: String = "Stopped",
    val incomingCalls: Int = 0,
    val outgoingCalls: Int = 0,
    val isRunning: Boolean = false,
    val webHostInfo: String = "",
    val activeCallDirection: String? = null // "IN", "OUT", or null
)

data class InCallState(
    val isActive: Boolean = false,
    val number: String = "",
    val callState: Int = Call.STATE_DISCONNECTED,
    val isMuted: Boolean = false,
    val isSpeaker: Boolean = false,
    val isGatewayBridged: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _gatewayState = MutableStateFlow(GatewayState())
    val gatewayState: StateFlow<GatewayState> = _gatewayState.asStateFlow()

    private val _inCallState = MutableStateFlow(InCallState())
    val inCallState: StateFlow<InCallState> = _inCallState.asStateFlow()

    private val _callLogs = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val callLogs: StateFlow<List<CallLogEntry>> = _callLogs.asStateFlow()

    private val _systemLogs = MutableStateFlow<List<String>>(emptyList())
    val systemLogs: StateFlow<List<String>> = _systemLogs.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                GatewayService.STATUS_ACTION -> {
                    val state = intent?.getStringExtra("state") ?: "IDLE"
                    val info = intent?.getStringExtra("info") ?: ""
                    val running = state != "STOPPED" && state != "ERROR"
                    _gatewayState.value = _gatewayState.value.copy(
                        status = state,
                        info = info,
                        isRunning = running,
                        activeCallDirection = if (state.contains("GSM_RINGING") || state.contains("GSM_ANSWERED")) "IN" 
                                             else if (state.contains("GSM_DIALING") || state.contains("SIP_CALLING")) "OUT"
                                             else null
                    )
                }
                GatewayService.LOG_ACTION -> {
                    val msg = intent?.getStringExtra("msg") ?: return
                    _systemLogs.value = (_systemLogs.value + msg).takeLast(100)
                }
            }
        }
    }

    init {
        application.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(GatewayService.STATUS_ACTION)
                addAction(GatewayService.LOG_ACTION)
            },
            Context.RECEIVER_EXPORTED
        )
        refreshLogs()
        pollInCallState()
    }

    fun refreshLogs() {
        viewModelScope.launch {
            _callLogs.value = CallLogStore.getEntries(getApplication())
            val totals = CallLogStore.getTotals(getApplication())
            _gatewayState.value = _gatewayState.value.copy(
                incomingCalls = totals.inCalls,
                outgoingCalls = totals.outCalls
            )
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            CallLogStore.clear(getApplication())
            _callLogs.value = emptyList()
            _gatewayState.value = _gatewayState.value.copy(incomingCalls = 0, outgoingCalls = 0)
        }
    }

    fun toggleGateway(server: String, port: Int, user: String, pass: String, localServer: Boolean) {
        val ctx = getApplication<Application>()
        if (_gatewayState.value.isRunning) {
            val i = Intent(ctx, GatewayService::class.java).apply { action = GatewayService.ACTION_STOP }
            ctx.startService(i)
        } else {
            val i = Intent(ctx, GatewayService::class.java).apply {
                action = GatewayService.ACTION_START
                putExtra(GatewayService.EXTRA_SERVER, server)
                putExtra(GatewayService.EXTRA_PORT, port)
                putExtra(GatewayService.EXTRA_USER, user)
                putExtra(GatewayService.EXTRA_PASS, pass)
                putExtra(GatewayService.EXTRA_LOCAL_SERVER, localServer)
            }
            ctx.startService(i)
        }
    }

    fun importConfigFromUrl(url: String) {
        viewModelScope.launch {
            // Placeholder for URL import logic
            // In a real app, we'd fetch JSON and update SharedPreferences
        }
    }

    // Polling in-call state is a simple bridge from GsmCallManager to Compose Flow
    private fun pollInCallState() {
        viewModelScope.launch {
            while (true) {
                val call = GsmCallManager.activeCall
                val state = GsmCallManager.activeCallState
                
                if (call != null) {
                    val number = call.details?.handle?.schemeSpecificPart ?: "Unknown"
                    _inCallState.value = _inCallState.value.copy(
                        isActive = true,
                        number = number,
                        callState = state,
                        isGatewayBridged = GsmCallManager.listener != null
                    )
                } else if (_inCallState.value.isActive) {
                    _inCallState.value = _inCallState.value.copy(isActive = false)
                    refreshLogs() // Refresh logs when call ends
                }
                kotlinx.coroutines.delay(500)
            }
        }
    }

    fun toggleMute() {
        val newMute = !_inCallState.value.isMuted
        _inCallState.value = _inCallState.value.copy(isMuted = newMute)
        GsmCallManager.setMuteMode(newMute)
    }

    fun toggleSpeaker() {
        val newSpeaker = !_inCallState.value.isSpeaker
        _inCallState.value = _inCallState.value.copy(isSpeaker = newSpeaker)
        GsmCallManager.setSpeakerMode(newSpeaker)
    }

    fun hangup() {
        GsmCallManager.hangupCall()
    }
    
    fun answer() {
        GsmCallManager.answerCall()
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unregisterReceiver(receiver)
    }
}
