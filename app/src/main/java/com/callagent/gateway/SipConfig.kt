package com.callagent.gateway

import android.content.Context
import android.content.SharedPreferences

/**
 * SIP credentials: read from the legacy "gateway" SharedPreferences, falling
 * back to [BuildConfig] defaults when keys are missing or blank.
 */
object SipConfig {

    const val PREFS_NAME = "gateway"

    const val KEY_SERVER = "server"
    const val KEY_PORT = "port"
    const val KEY_USER = "user"
    const val KEY_PASS = "pass"
    const val KEY_LOCAL_SERVER = "local_server"
    const val KEY_AUTOCONNECT = "autoconnect"
    const val KEY_OUTBOUND_TARGET = "outbound_target"

    // OpenAI Realtime WebSocket-direct transport (alternative to SIP/RTP).
    const val KEY_REALTIME_ENABLED = "realtime_enabled"
    const val KEY_REALTIME_TOKEN_URL = "realtime_token_url"
    const val KEY_REALTIME_MODEL = "realtime_model"
    const val KEY_REALTIME_VOICE = "realtime_voice"

    val defaultServer: String get() = BuildConfig.DEFAULT_SIP_SERVER
    val defaultUser: String get() = BuildConfig.DEFAULT_SIP_USER
    val defaultPort: Int get() = BuildConfig.DEFAULT_SIP_PORT
    const val DEFAULT_PASS = ""
    const val DEFAULT_LOCAL_SERVER = false
    const val DEFAULT_AUTOCONNECT = true
    const val DEFAULT_OUTBOUND_TARGET = "+12015029074"
    val defaultRealtimeEnabled: Boolean get() = BuildConfig.DEFAULT_REALTIME_ENABLED
    val defaultRealtimeTokenUrl: String get() = BuildConfig.DEFAULT_REALTIME_TOKEN_URL
    val defaultRealtimeModel: String get() = BuildConfig.DEFAULT_REALTIME_MODEL
    val defaultRealtimeVoice: String get() = BuildConfig.DEFAULT_REALTIME_VOICE

    fun openPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun resolveServer(prefs: SharedPreferences): String =
        prefs.getString(KEY_SERVER, null)?.takeIf { it.isNotBlank() } ?: defaultServer

    fun resolveUser(prefs: SharedPreferences): String =
        prefs.getString(KEY_USER, null)?.takeIf { it.isNotBlank() } ?: defaultUser

    fun resolvePort(prefs: SharedPreferences): Int =
        if (prefs.contains(KEY_PORT)) prefs.getInt(KEY_PORT, defaultPort) else defaultPort

    fun resolvePass(prefs: SharedPreferences): String =
        prefs.getString(KEY_PASS, null) ?: DEFAULT_PASS

    fun resolveLocalServer(prefs: SharedPreferences): Boolean =
        if (prefs.contains(KEY_LOCAL_SERVER)) {
            prefs.getBoolean(KEY_LOCAL_SERVER, DEFAULT_LOCAL_SERVER)
        } else {
            DEFAULT_LOCAL_SERVER
        }

    fun resolveAutoconnect(prefs: SharedPreferences): Boolean =
        if (prefs.contains(KEY_AUTOCONNECT)) {
            prefs.getBoolean(KEY_AUTOCONNECT, DEFAULT_AUTOCONNECT)
        } else {
            DEFAULT_AUTOCONNECT
        }

    fun resolveOutboundTarget(prefs: SharedPreferences): String =
        prefs.getString(KEY_OUTBOUND_TARGET, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_OUTBOUND_TARGET

    // Realtime WS mode is active only when explicitly enabled AND a token URL is
    // configured (the bridge-worker /token endpoint) — an empty URL means off.
    fun resolveRealtimeTokenUrl(prefs: SharedPreferences): String =
        prefs.getString(KEY_REALTIME_TOKEN_URL, null)?.takeIf { it.isNotBlank() } ?: defaultRealtimeTokenUrl

    fun resolveRealtimeEnabled(prefs: SharedPreferences): Boolean {
        val on = if (prefs.contains(KEY_REALTIME_ENABLED)) {
            prefs.getBoolean(KEY_REALTIME_ENABLED, defaultRealtimeEnabled)
        } else {
            defaultRealtimeEnabled
        }
        return on && resolveRealtimeTokenUrl(prefs).isNotBlank()
    }

    fun resolveRealtimeModel(prefs: SharedPreferences): String =
        prefs.getString(KEY_REALTIME_MODEL, null)?.takeIf { it.isNotBlank() } ?: defaultRealtimeModel

    fun resolveRealtimeVoice(prefs: SharedPreferences): String =
        prefs.getString(KEY_REALTIME_VOICE, null)?.takeIf { it.isNotBlank() } ?: defaultRealtimeVoice

    fun isConfigured(prefs: SharedPreferences): Boolean =
        resolveServer(prefs).isNotBlank() && resolveUser(prefs).isNotBlank()

    data class Resolved(
        val server: String,
        val port: Int,
        val user: String,
        val pass: String,
        val localServer: Boolean,
        val autoconnect: Boolean,
        val outboundTarget: String,
        val realtimeEnabled: Boolean,
        val realtimeTokenUrl: String,
        val realtimeModel: String,
        val realtimeVoice: String,
    )

    fun resolve(prefs: SharedPreferences): Resolved = Resolved(
        server = resolveServer(prefs),
        port = resolvePort(prefs),
        user = resolveUser(prefs),
        pass = resolvePass(prefs),
        localServer = resolveLocalServer(prefs),
        autoconnect = resolveAutoconnect(prefs),
        outboundTarget = resolveOutboundTarget(prefs),
        realtimeEnabled = resolveRealtimeEnabled(prefs),
        realtimeTokenUrl = resolveRealtimeTokenUrl(prefs),
        realtimeModel = resolveRealtimeModel(prefs),
        realtimeVoice = resolveRealtimeVoice(prefs),
    )
}
