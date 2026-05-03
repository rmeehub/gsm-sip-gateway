package com.callagent.gateway.gsm

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import com.callagent.gateway.DeviceProfile
import com.callagent.gateway.RootShell
import com.callagent.gateway.web.WebServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.callagent.gateway.service.CallLogStore
import com.callagent.gateway.service.CallLogEntry

/**
 * GSM call manager: answers/makes/hangs up GSM calls, tracks state.
 *
 * Calls are controlled through the InCallService (GsmCallService).
 * Audio routing uses device-specific mixer controls via [DeviceProfile].
 *
 * SIP→GSM: AudioTrack (USAGE_MEDIA / deep-buffer) → incall_music →
 * HAL injects STREAM_MUSIC digitally into voice TX (uplink).
 *
 * GSM→SIP: VOICE_CALL capture provides digital uplink+downlink audio.
 *
 * ALL tinymix commands are batched into a single su call to minimise
 * JVM spawning on low-end devices.
 */
object GsmCallManager {

    private const val TAG = "GsmCallManager"

    /** Active device profile — initialized on first use. */
    val profile: DeviceProfile by lazy { DeviceProfile.detect() }

    // Current active GSM call
    @Volatile var activeCall: Call? = null; private set
    @Volatile var activeCallState: Int = Call.STATE_NEW; private set
    @Volatile var inCallService: InCallService? = null; private set

    @Volatile var listener: Listener? = null

    private var callStartTime: Long = 0L
    private var callDirection: String = "OUT"

    /** Optional callback for routing important audio diagnostics to the
     *  app log viewer (Settings tab).  Set by GatewayService. */
    @Volatile var logCallback: ((String) -> Unit)? = null

    /** Log to both Android logcat AND the app log viewer. */
    private fun appLog(msg: String) {
        Log.i(TAG, msg)
        logCallback?.invoke(msg)
    }

    interface Listener {
        /** Incoming GSM call ringing — caller number provided */
        fun onIncomingGsmCall(call: Call, number: String)
        /** GSM call connected (active) */
        fun onGsmCallActive(call: Call)
        /** GSM call state changed */
        fun onGsmCallStateChanged(call: Call, state: Int)
        /** GSM call ended */
        fun onGsmCallEnded(call: Call)
    }

    // ── InCallService callbacks ─────────────────────────

    fun onCallAdded(call: Call, service: InCallService) {
        inCallService = service
        activeCall = call
        activeCallState = call.state

        val number = call.details?.handle?.schemeSpecificPart ?: "unknown"
        
        // Log audio state when call is added
        val supportedRoutes = service.callAudioState?.supportedRouteMask ?: 0
        val currentRoute = service.callAudioState?.currentRoute ?: 0
        val isMuted = service.callAudioState?.isMuted ?: false
        val modeName = if (listener != null) {
            if (com.callagent.gateway.web.WebServer.isRunning()) "SERVER (PBX)" else "CLIENT"
        } else "STANDALONE"
        
        val routeNames = mutableListOf<String>()
        if (supportedRoutes and CallAudioState.ROUTE_EARPIECE != 0) routeNames.add("EAR")
        if (supportedRoutes and CallAudioState.ROUTE_SPEAKER != 0) routeNames.add("SPK")
        if (supportedRoutes and CallAudioState.ROUTE_WIRED_HEADSET != 0) routeNames.add("HS")
        if (supportedRoutes and CallAudioState.ROUTE_BLUETOOTH != 0) routeNames.add("BT")
        
        appLog("[$modeName] Call added: $number, state=${call.state}, routes=[${routeNames.joinToString(", ")}], current=${currentRoute}, muted=$isMuted")

        when (call.state) {
            Call.STATE_RINGING -> {
                callDirection = "IN"
                Log.i(TAG, "Incoming GSM call from $number")
                // Silence the ringtone immediately — this is a gateway device,
                // not a user-facing phone.  The call will be auto-answered
                // once the SIP leg is established.
                try {
                    val am = service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    am.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
                } catch (e: Exception) {
                    Log.w(TAG, "Ringer silence failed: ${e.message}")
                }
                
                // Mute vibration (Root)
                if (listener != null) {
                    RootShell.exec("cmd vibrator cancel 2>/dev/null", timeoutMs = 500)
                }

                if (listener != null) {
                    listener?.onIncomingGsmCall(call, number)
                } else {
                    notifyStandaloneDialer(service, number)
                }
            }
            Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                callDirection = "OUT"
                Log.i(TAG, "Outgoing GSM call to $number")
            }
            Call.STATE_ACTIVE -> {
                if (callStartTime == 0L) callStartTime = System.currentTimeMillis()
                Log.i(TAG, "GSM call active: $number")
                configureAudioBridge()
                listener?.onGsmCallActive(call)
            }
        }
    }

    fun onCallRemoved(call: Call) {
        Log.i(TAG, "GSM call removed")
        if (activeCall == call) {
            activeCall = null
            activeCallState = Call.STATE_DISCONNECTED
        }
        restoreAudio()
        listener?.onGsmCallEnded(call)
    }

    private fun stateToString(state: Int): String {
        return when (state) {
            Call.STATE_NEW -> "NEW"
            Call.STATE_CONNECTING -> "CONNECTING"
            Call.STATE_RINGING -> "RINGING"
            Call.STATE_DIALING -> "DIALING"
            Call.STATE_DISCONNECTED -> "DISCONNECTED"
            Call.STATE_DISCONNECTING -> "DISCONNECTING"
            else -> "STATE_$state"
        }
    }

    fun onCallStateChanged(call: Call, state: Int) {
        activeCallState = state

        // Log audio state on any state change
        inCallService?.let { service ->
            val currentRoute = service.callAudioState?.currentRoute ?: 0
            val isMuted = service.callAudioState?.isMuted ?: false
            val modeName = if (listener != null) {
                if (com.callagent.gateway.web.WebServer.isRunning()) "SERVER (PBX)" else "CLIENT"
            } else "STANDALONE"
            
            val routeName = when (currentRoute) {
                CallAudioState.ROUTE_EARPIECE -> "EARPIECE"
                CallAudioState.ROUTE_SPEAKER -> "SPEAKER"
                CallAudioState.ROUTE_WIRED_HEADSET -> "HEADSET"
                CallAudioState.ROUTE_BLUETOOTH -> "BLUETOOTH"
                else -> "UNKNOWN"
            }
            appLog("[$modeName] State changed: ${stateToString(state)}, route=$routeName, muted=$isMuted")
        }

        when (state) {
            Call.STATE_RINGING -> {
                callDirection = "IN"
                // Handle calls that arrive as STATE_NEW in onCallAdded and
                // transition to RINGING via the callback.  Without this,
                // the orchestrator never learns about the incoming call.
                val number = call.details?.handle?.schemeSpecificPart ?: "unknown"
                Log.i(TAG, "GSM call ringing: $number (via state change)")
                
                // Mute vibration (Root)
                if (listener != null) {
                    RootShell.exec("cmd vibrator cancel 2>/dev/null", timeoutMs = 500)
                }

                if (listener != null) {
                    listener?.onIncomingGsmCall(call, number)
                } else {
                    inCallService?.let { notifyStandaloneDialer(it, number) }
                }
            }
            Call.STATE_ACTIVE -> {
                if (callStartTime == 0L) callStartTime = System.currentTimeMillis()
                Log.i(TAG, "GSM call active")
                configureAudioBridge()
                listener?.onGsmCallActive(call)
            }
            Call.STATE_DISCONNECTED -> {
                Log.i(TAG, "GSM call disconnected, state=$state")
                
                // Log audio state on disconnect
                val modeName = if (listener != null) {
                    if (com.callagent.gateway.web.WebServer.isRunning()) "SERVER (PBX)" else "CLIENT"
                } else "STANDALONE"
                appLog("[$modeName] Call ended - audio routing restored")
                
                // Save Call Log
                val timestamp = if (callStartTime > 0L) callStartTime else System.currentTimeMillis()
                val durationSec = if (callStartTime > 0L) (System.currentTimeMillis() - callStartTime) / 1000 else 0L
                val number = call.details?.handle?.schemeSpecificPart ?: "unknown"
                val type = if (listener != null) "GATEWAY" else "STANDALONE"
                val entry = CallLogEntry(callDirection, number, timestamp, durationSec, type)
                
                // Use application context to avoid leaks/null issues
                val context = inCallService?.applicationContext
                context?.let { ctx ->
                    CoroutineScope(Dispatchers.IO).launch {
                        CallLogStore.addEntry(ctx, entry)
                        Log.i(TAG, "Call log saved: $number ($callDirection, $durationSec s)")
                    }
                }

                callStartTime = 0L
                listener?.onGsmCallEnded(call)
                if (activeCall == call) {
                    activeCall = null
                }
            }
        }
        listener?.onGsmCallStateChanged(call, state)
    }

    // ── Call control ────────────────────────────────────

    /** Answer a ringing GSM call */
    fun answerCall(call: Call? = activeCall) {
        call?.let {
            Log.i(TAG, "Answering GSM call")
            it.answer(it.details.videoState)
        }
    }

    private fun notifyStandaloneDialer(context: Context, number: String) {
        // Gateway off — launch MainActivity as a standalone dialer
        val intent = Intent(context, Class.forName("com.callagent.gateway.MainActivity")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("incoming_call", true)
            putExtra("number", number)
        }
        context.startActivity(intent)
    }

    /** Reject a ringing GSM call */
    fun rejectCall(call: Call? = activeCall) {
        call?.let {
            Log.i(TAG, "Rejecting GSM call")
            it.reject(false, "")
        }
    }

    /** Hang up active GSM call */
    fun hangupCall(call: Call? = activeCall) {
        call?.let {
            Log.i(TAG, "Hanging up GSM call")
            it.disconnect()
        }
    }

    /** Place outgoing GSM call via the SIM */
    fun makeCall(context: Context, destination: String) {
        Log.i(TAG, "Making GSM call to $destination")
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$destination"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // ── Standalone Dialer Controls ──────────────────────

    fun setSpeakerMode(enabled: Boolean) {
        if (enabled) {
            inCallService?.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            Log.i(TAG, "Standalone: Speaker route selected")
        } else {
            val supported = inCallService?.callAudioState?.supportedRouteMask ?: 0
            val newRoute = when {
                (supported and CallAudioState.ROUTE_WIRED_HEADSET) != 0 -> CallAudioState.ROUTE_WIRED_HEADSET
                (supported and CallAudioState.ROUTE_BLUETOOTH) != 0 -> CallAudioState.ROUTE_BLUETOOTH
                else -> CallAudioState.ROUTE_EARPIECE
            }
            inCallService?.setAudioRoute(newRoute)
            Log.i(TAG, "Standalone: Earpiece/Headset route selected ($newRoute)")
        }
    }

    fun setMuteMode(muted: Boolean) {
        inCallService?.setMuted(muted)
        Log.i(TAG, "Standalone: Mute state set to $muted")
    }

    fun playDtmfTone(c: Char) {
        activeCall?.playDtmfTone(c)
    }

    fun stopDtmfTone() {
        activeCall?.stopDtmfTone()
    }

    /** Music volume percent — from device profile. */
    val MUSIC_VOL_PERCENT: Int get() = profile.musicVolPercent

    /** Run mixer discovery once on first audio bridge setup. */
    @Volatile private var discoveryDone = false

    private fun runMixerDiscovery() {
        if (discoveryDone) return
        discoveryDone = true
        Thread({
            try {
                val discovery = DeviceProfile.discoverMixerControls()
                for (line in discovery.lines()) {
                    if (line.isNotBlank()) Log.i(TAG, "MixerDiscovery: $line")
                }
                // Send summary to app log viewer (not the full dump)
                val cards = discovery.lines()
                    .filter { it.contains("[") && it.contains("]") && it.contains(":") }
                    .joinToString(", ") { it.trim() }
                val tinymix = if (DeviceProfile.tinymixBin.isNotEmpty())
                    DeviceProfile.tinymixBin else "NOT FOUND"
                appLog("ALSA: tinymix=$tinymix cards=[$cards]")
            } catch (e: Exception) {
                appLog("Mixer discovery failed: ${e.message}")
            }
        }, "MixerDiscovery").start()
    }

    /** Configure audio for GSM↔SIP bridge using the active device profile. */
    private fun configureAudioBridge() {
        // Always log audio routing info - for both gateway and standalone modes
        val isGatewayMode = listener != null
        val isServerMode = WebServer.isRunning()
        val modeName = when {
            isGatewayMode && isServerMode -> "SERVER (PBX) MODE"
            isGatewayMode -> "CLIENT MODE"
            else -> "STANDALONE MODE"
        }
        
        // Run ABOX/ALSA discovery on first call for diagnostics
        runMixerDiscovery()
        
        inCallService?.let { service ->
            val audioManager = service.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            
            // Log current supported routes for diagnostics
            val supportedRoutes = service.callAudioState?.supportedRouteMask ?: 0
            val currentRoute = service.callAudioState?.currentRoute ?: 0
            
            val routeNames = mutableListOf<String>()
            if (supportedRoutes and CallAudioState.ROUTE_EARPIECE != 0) routeNames.add("EARPIECE")
            if (supportedRoutes and CallAudioState.ROUTE_SPEAKER != 0) routeNames.add("SPEAKER")
            if (supportedRoutes and CallAudioState.ROUTE_WIRED_HEADSET != 0) routeNames.add("HEADSET")
            if (supportedRoutes and CallAudioState.ROUTE_BLUETOOTH != 0) routeNames.add("BLUETOOTH")
            
            val routeName = when (currentRoute) {
                CallAudioState.ROUTE_EARPIECE -> "EARPIECE"
                CallAudioState.ROUTE_SPEAKER -> "SPEAKER"
                CallAudioState.ROUTE_WIRED_HEADSET -> "HEADSET"
                CallAudioState.ROUTE_BLUETOOTH -> "BLUETOOTH"
                else -> "UNKNOWN"
            }
            
            appLog("=== AUDIO ROUTING ($modeName) ===")
            appLog("Device Profile: ${profile.name}")
            appLog("Available Routes: [${routeNames.joinToString(", ")}]")
            appLog("Current Route: $routeName (${currentRoute})")
            appLog("tinymix: ${if (DeviceProfile.tinymixBin.isNotEmpty()) DeviceProfile.tinymixBin else "NOT FOUND"}")
            appLog("requireSpeaker: ${profile.requireSpeakerMode}")
            
            // Explain audio flow for both modes
            if (isServerMode) {
                appLog("Audio Flow: GSM Caller <-> This Device (Earpiece/Mic) <-> RTP <-> SIP Client (Zoiper)")
                appLog("  OUT: GSM voice -> Internal Mic -> AudioRecord -> RTP -> SIP Client")
                appLog("  IN : SIP Client -> RTP -> AudioTrack -> Earpiece/Speaker -> GSM Caller")
            } else if (isGatewayMode) {
                appLog("Audio Flow: GSM Caller <-> This Device (Earpiece/Mic) <-> RTP <-> Asterisk")
                appLog("  OUT: GSM voice -> Internal Mic -> AudioRecord -> RTP -> Asterisk/SIP Server")
                appLog("  IN : Asterisk -> RTP -> AudioTrack -> Earpiece/Speaker -> GSM Caller")
            }
            appLog("================================")
            
            // Continue with audio bridge configuration only in gateway mode
            if (!isGatewayMode) {
                // Standalone mode - just log and return, let Android handle routing
                appLog("Standalone: Using system audio routing")
                audioManager?.let { am ->
                    appLog("Audio mode: ${am.mode}, stream volumes: voice=${am.getStreamVolume(AudioManager.STREAM_VOICE_CALL)}/${am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)}, ring=${am.getStreamVolume(AudioManager.STREAM_RING)}/${am.getStreamMaxVolume(AudioManager.STREAM_RING)}")
                }
                return
            }

            // GATEWAY MODE: Route to EARPIECE so the internal mic+speaker
            // handle the GSM audio path directly. This is the correct path
            // for bridging — GSM voice flows through the earpiece hardware
            // (modem → ear speaker) and we capture it via VOICE_CALL source.
            // Using SPEAKER here causes: loud bleed into mic → echo for caller,
            // and AudioRecord may capture speaker output instead of modem audio.
            // Only override to speaker if the device profile specifically requires it
            // for hardware-level audio injection (some MSM/Exynos quirks).
            
            if (profile.requireSpeakerMode) {
                // Legacy path: device profile mandates speaker for correct HAL routing
                service.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
                appLog("Audio bridge: SPEAKER (required by device profile)")
                appLog("*** NOTE: Speaker mode - mic will capture speaker output, may cause echo ***")
            } else {
                // Default path: earpiece — cleanest audio with no echo
                // But ensure earpiece is available
                if (supportedRoutes and CallAudioState.ROUTE_EARPIECE != 0) {
                    service.setAudioRoute(CallAudioState.ROUTE_EARPIECE)
                    appLog("Audio bridge: EARPIECE (internal mic + speaker) - OPTIMAL for bridge")
                } else if (supportedRoutes and CallAudioState.ROUTE_SPEAKER != 0) {
                    service.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
                    appLog("Audio bridge: SPEAKER (earpiece not available, fallback)")
                } else {
                    appLog("Audio bridge: DEFAULT (no specific route available)")
                }
            }

            audioManager?.let { am ->
                am.isMicrophoneMute = false
                enforceVolumes(am)

                // Delay mixer/volume setup until route change settles.
                Thread({
                        try {
                            Thread.sleep(profile.routeChangeDelayMs)
                            enforceVolumes(am)
                            batchMixerSetup()
                        } catch (_: Exception) {}
                    }, "VolEnforce").start()

                    val route = if (profile.requireSpeakerMode) "speaker" else "earpiece"
                    val tinymixStatus = if (DeviceProfile.tinymixBin.isNotEmpty()) "available" else "NOT FOUND"
                    appLog("Audio bridge: $route, mode=${am.mode}, tinymix=$tinymixStatus, profile=${profile.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure audio: ${e.message}")
        }
    }

    /** Set audio stream volumes for the GSM↔SIP bridge.
     *  Called multiple times: immediately, after delayed route change,
     *  and from RtpSession as a secondary safeguard. */
    fun enforceVolumes(am: AudioManager) {
        // Clear any stale ADJUST_MUTE flag from a previous call.
        // CRITICAL: Do NOT use ADJUST_MUTE on STREAM_VOICE_CALL — on
        // MSM8930 it kills the incall_music injection path, preventing
        // the agent's audio from reaching the GSM caller.  Speaker
        // silencing is handled by muteVoiceRx() at the ALSA mixer level.
        try {
            am.adjustStreamVolume(AudioManager.STREAM_VOICE_CALL, AudioManager.ADJUST_UNMUTE, 0)
        } catch (_: SecurityException) {}
        // Voice call volume: controls caller's voice on speaker.
        // MSM8930: minimum (1) — speaker silenced by muteVoiceRx via tinymix.
        // Exynos 9820: 80% — no muteVoiceRx, need loud speaker for mic capture.
        // Volume=0 can confuse audio policy into treating call as inactive.
        try {
            val vcVol = if (profile.voiceCallVolPercent > 0) {
                val maxVc = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                (maxVc * profile.voiceCallVolPercent / 100).coerceAtLeast(1)
            } else {
                1
            }
            am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, vcVol, 0)
        } catch (_: SecurityException) {}
        // Music stream controls incall_music injection level into
        // the modem uplink.  Lower value = quieter speaker + quieter
        // agent voice for the GSM caller.
        val maxMusic = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val musicVol = (maxMusic * MUSIC_VOL_PERCENT / 100).coerceAtLeast(1)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, musicVol, 0)
        // Read back actual values to confirm they stuck
        val actualVoice = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        val actualMusic = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val muted = am.isStreamMute(AudioManager.STREAM_VOICE_CALL)
        appLog("Vol: voice=$actualVoice(m=$muted), music=$actualMusic/$maxMusic(target=$musicVol)")
    }

    /** Restore audio state when call ends */
    private fun restoreAudio() {
        if (listener == null) return // Standalone mode
        
        try {
            // Single su call to restore all mixer controls
            batchMixerRestore()

            inCallService?.let { service ->
                service.setAudioRoute(CallAudioState.ROUTE_EARPIECE)

                val audioManager = service.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audioManager?.let { am ->
                    am.isMicrophoneMute = false
                    // Clear incall_music HAL parameter for clean state on next call
                    if (profile.incallMusicParam.isNotEmpty()) {
                        am.setParameters("${profile.incallMusicParam}=false")
                    }

                    // Unmute voice call stream and restore volume for normal phone use
                    try {
                        am.adjustStreamVolume(AudioManager.STREAM_VOICE_CALL, AudioManager.ADJUST_UNMUTE, 0)
                    } catch (_: SecurityException) {}
                    try {
                        val maxVc = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                        am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, (maxVc * 2 / 3).coerceAtLeast(1), 0)
                    } catch (_: SecurityException) {}
                    Log.i(TAG, "Audio restored: earpiece, VoiceRx unmuted, echoRef=SLIM_RX, incall_music=false")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore audio: ${e.message}")
        }
    }

    /**
     * Set up mixer controls for audio bridge using the device profile.
     * All commands batched into a single su call for efficiency.
     *
     * Commands reference bare 'tinymix' — [DeviceProfile.resolveCmd] replaces
     * it with the discovered full path at runtime.
     */
    fun batchMixerSetup() {
        if (profile.mixerSetupCmd.isEmpty()) {
            appLog("Mixer: no commands for ${profile.name}")
            return
        }
        val resolvedSetup = DeviceProfile.resolveCmd(profile.mixerSetupCmd)
        if (resolvedSetup.isEmpty()) {
            appLog("Mixer: tinymix NOT FOUND — cannot set controls for ${profile.name}")
            return
        }
        try {
            val bin = DeviceProfile.tinymixBin
            // Step 1: Readback BEFORE — see what HAL set during call setup
            val before = RootShell.execForOutput(buildString {
                append("echo 'NSRC0B:'; $bin 'ABOX NSRC0 Bridge' 2>&1; ")
                append("echo 'NSRC1B:'; $bin 'ABOX NSRC1 Bridge' 2>&1; ")
                append("echo 'NSRC0:'; $bin 'ABOX NSRC0' 2>&1; ")
                append("echo 'NSRC1:'; $bin 'ABOX NSRC1' 2>&1; ")
                append("echo 'SPUS0:'; $bin 'ABOX SPUS OUT0' 2>&1")
            }, timeoutMs = 8000)
            appLog("Mixer BEFORE: $before")

            // Step 2: Run mixer setup commands (all ABOX controls on card 0)
            // Use execForOutput to capture discovery/diagnostic output from setup commands
            val setupOutput = RootShell.execForOutput(resolvedSetup, timeoutMs = 8000)
            if (setupOutput.isNotBlank()) appLog("Mixer setup: $setupOutput")

            // Step 3: Readback AFTER — verify controls were actually changed
            val readback = RootShell.execForOutput(buildString {
                append("echo 'NSRC0B:'; $bin 'ABOX NSRC0 Bridge' 2>&1; ")
                append("echo 'NSRC1B:'; $bin 'ABOX NSRC1 Bridge' 2>&1; ")
                append("echo 'NSRC2B:'; $bin 'ABOX NSRC2 Bridge' 2>&1; ")
                append("echo 'NSRC0:'; $bin 'ABOX NSRC0' 2>&1; ")
                append("echo 'NSRC1:'; $bin 'ABOX NSRC1' 2>&1; ")
                append("echo 'SPUS0:'; $bin 'ABOX SPUS OUT0' 2>&1")
            }, timeoutMs = 10000)
            appLog("Mixer AFTER: $readback")
        } catch (e: Exception) {
            appLog("Mixer setup FAILED: ${e.message}")
        }
    }

    /** Restore mixer state when call ends using the device profile. */
    fun batchMixerRestore() {
        if (profile.mixerRestoreCmd.isEmpty()) {
            Log.i(TAG, "batchMixerRestore: no mixer commands for ${profile.name}")
            return
        }
        val resolvedRestore = DeviceProfile.resolveCmd(profile.mixerRestoreCmd)
        if (resolvedRestore.isEmpty()) {
            Log.i(TAG, "batchMixerRestore: tinymix not found, skipping")
            return
        }
        try {
            RootShell.exec(resolvedRestore)
            appLog("Mixer restored")
        } catch (e: Exception) {
            appLog("Mixer restore FAILED: ${e.message}")
        }
    }

    /** Check if a GSM call is currently active */
    val isCallActive: Boolean
        get() = activeCall != null && activeCallState == Call.STATE_ACTIVE

    /** Get current call number */
    val currentNumber: String?
        get() = activeCall?.details?.handle?.schemeSpecificPart
}
