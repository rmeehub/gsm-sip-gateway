package com.callagent.gateway.web

import android.content.Context
import android.util.Log
import com.callagent.gateway.service.GatewayService
import com.callagent.gateway.sip.SipBuilder
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

object WebServer {
    private const val TAG = "KtorWebServer"
    private var engine: ApplicationEngine? = null

    // SIP extension store: extension → password
    private val extensions = mutableMapOf<String, String>()
    // Callback to get live registered clients from SipClient
    var getRegisteredClients: (() -> Map<String, com.callagent.gateway.sip.SipClient.PbxClient>)? = null

    private const val PREFS_NAME = "pbx_extensions"

    private fun loadExtensions(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val extsString = prefs.getString("extensions", null)
        extensions.clear()
        if (extsString != null) {
            try {
                val json = JSONObject(extsString)
                val iter = json.keys()
                while (iter.hasNext()) {
                    val key = iter.next() as String
                    extensions[key] = json.getString(key)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading extensions", e)
            }
        }
        
        // Ensure default extensions exist if empty
        if (extensions.isEmpty()) {
            extensions["100"] = "1234"
            extensions["101"] = "1234"
            saveExtensions(context)
        }
    }

    private fun saveExtensions(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = JSONObject()
        extensions.forEach { (ext, pass) -> json.put(ext, pass) }
        prefs.edit().putString("extensions", json.toString()).commit()
    }

    fun start(context: Context, port: Int = 8080) {
        if (engine != null) return

        loadExtensions(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                engine = embeddedServer(CIO, host = "0.0.0.0", port = port) {
                    routing {
                        get("/") {
                            call.respondText(getHtml(), ContentType.Text.Html)
                        }

                        get("/api/status") {
                            val prefs = context.getSharedPreferences("gateway", Context.MODE_PRIVATE)
                            val status = JSONObject().apply {
                                put("server", prefs.getString("server", ""))
                                put("port", prefs.getInt("port", 5060))
                                put("local_server", prefs.getBoolean("local_server", false))
                                put("running", true)
                            }
                            call.respondText(status.toString(), ContentType.Application.Json)
                        }

                        // List configured extensions
                        get("/api/extensions") {
                            val arr = JSONArray()
                            extensions.forEach { (ext, _) ->
                                arr.put(JSONObject().apply {
                                    put("extension", ext)
                                    put("password", "****")
                                })
                            }
                            call.respondText(arr.toString(), ContentType.Application.Json)
                        }

                        // Add or update extension
                        post("/api/extensions") {
                            val body = call.receiveText()
                            val json = JSONObject(body)
                            val ext = json.optString("extension").trim()
                            val pass = json.optString("password").trim()
                            if (ext.isNotEmpty() && pass.isNotEmpty()) {
                                extensions[ext] = pass
                                saveExtensions(context)
                                call.respondText(
                                    JSONObject().apply { put("ok", true) }.toString(),
                                    ContentType.Application.Json
                                )
                            } else {
                                call.respond(HttpStatusCode.BadRequest, "extension and password required")
                            }
                        }

                        // Delete extension
                        delete("/api/extensions/{ext}") {
                            val ext = call.parameters["ext"] ?: ""
                            extensions.remove(ext)
                            saveExtensions(context)
                            call.respondText(
                                JSONObject().apply { put("ok", true) }.toString(),
                                ContentType.Application.Json
                            )
                        }

                        // Live registered SIP clients
                        get("/api/clients") {
                            val arr = JSONArray()
                            getRegisteredClients?.invoke()?.forEach { (ext, client) ->
                                val contact = client.contact
                                val expiry = client.expiry
                                val remaining = ((expiry - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                                arr.put(JSONObject().apply {
                                    put("extension", ext)
                                    put("contact", contact)
                                    put("expires_in", remaining)
                                    put("online", remaining > 0)
                                })
                            }
                            call.respondText(arr.toString(), ContentType.Application.Json)
                        }

                        // Export system logs
                        get("/api/logs") {
                            val logs = GatewayService.drainLogBuffer()
                            val format = call.parameters["format"] ?: "text"
                            if (format == "json") {
                                val arr = JSONArray()
                                logs.forEach { arr.put(it) }
                                call.respondText(arr.toString(), ContentType.Application.Json)
                            } else {
                                call.respondText(logs.joinToString("\n"), ContentType.Text.Plain)
                            }
                        }

                        // Real-time log stream via Server-Sent Events
                        get("/api/logs/stream") {
                            call.respondText("text/event-stream", ContentType.Text.Plain) {
                                GatewayService.logBuffer.forEach { log ->
                                    append("data: $log\n\n")
                                }
                            }
                        }
                    }
                }.start(wait = false)
                Log.i(TAG, "Web UI started on port $port")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Web UI", e)
            }
        }
    }

    fun stop() {
        engine?.stop(1000, 2000)
        engine = null
        Log.i(TAG, "Web UI stopped")
    }

    /** Check if web server is currently running */
    fun isRunning(): Boolean = engine != null

    /** Check if a REGISTER request is for a valid extension (password validation) */
    fun isValidExtension(user: String, password: String): Boolean {
        return extensions[user] == password
    }

    /** Check if an extension exists in the configuration */
    fun extensionExists(user: String): Boolean {
        return extensions.containsKey(user)
    }

    /** Validate credentials from Authorization header */
    fun validateCredentials(user: String, authHeader: String): Boolean {
        val expectedPass = extensions[user] ?: return false
        
        // Parse Digest authentication
        if (authHeader.startsWith("Digest ", ignoreCase = true)) {
            val authParams = authHeader.removePrefix("Digest ").split(",")
                .associate {
                    val parts = it.trim().split("=", limit = 2)
                    if (parts.size == 2) {
                        parts[0].trim() to parts[1].trim().removeSurrounding("\"")
                    } else {
                        "" to ""
                    }
                }
            
            val realm = authParams["realm"] ?: return false
            val username = authParams["username"] ?: return false
            val nonce = authParams["nonce"] ?: return false
            val response = authParams["response"] ?: return false
            val uri = authParams["uri"] ?: return false
            
            // For simplicity, do basic MD5 check - full RFC 2831 implementation would be more complete
            val ha1 = md5("$username:$realm:$expectedPass")
            val ha2 = md5("REGISTER:$uri")
            val expectedResponse = md5("$ha1:$nonce:$ha2")
            
            return response == expectedResponse
        }
        
        // Basic auth fallback
        if (authHeader.startsWith("Basic ", ignoreCase = true)) {
            val encoded = authHeader.removePrefix("Basic ")
            val decoded = String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT))
            val (u, p) = decoded.split(":", limit = 2)
            return u == user && p == expectedPass
        }
        
        return false
    }

    private fun md5(input: String): String {
        try {
            val md = java.security.MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            return input // Fallback
        }
    }

    /** Check if a client is authenticated for PBX mode */
    fun isClientAuthenticated(extension: String): Boolean {
        val client = registeredClients[extension] ?: return false
        return client.authVerified && client.expiry > System.currentTimeMillis()
    }

    private fun getHtml(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>GSM Gateway PBX</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0f172a; color: #e2e8f0; min-height: 100vh; padding: 24px; }
  .header { display: flex; align-items: center; gap: 12px; margin-bottom: 32px; }
  .header h1 { font-size: 1.5rem; font-weight: 700; }
  .badge { padding: 4px 10px; border-radius: 999px; font-size: 0.75rem; font-weight: 600; }
  .badge-green { background: #16a34a22; color: #4ade80; border: 1px solid #16a34a44; }
  .badge-blue  { background: #2563eb22; color: #60a5fa; border: 1px solid #2563eb44; }
  .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 24px; }
  @media (max-width: 600px) { .grid { grid-template-columns: 1fr; } }
  .card { background: #1e293b; border: 1px solid #334155; border-radius: 12px; padding: 20px; }
  .card h2 { font-size: 1rem; font-weight: 600; color: #94a3b8; margin-bottom: 16px; text-transform: uppercase; letter-spacing: 0.05em; }
  .stat { font-size: 1.75rem; font-weight: 700; color: #f1f5f9; }
  table { width: 100%; border-collapse: collapse; }
  th { text-align: left; padding: 10px 12px; font-size: 0.75rem; color: #64748b; text-transform: uppercase; letter-spacing: 0.05em; background: #0f172a; }
  td { padding: 12px; border-top: 1px solid #1e293b; font-size: 0.875rem; }
  .dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; margin-right: 6px; }
  .dot-green { background: #4ade80; box-shadow: 0 0 6px #4ade80; }
  .dot-grey  { background: #475569; }
  input { background: #0f172a; border: 1px solid #334155; color: #e2e8f0; border-radius: 8px; padding: 8px 12px; font-size: 0.875rem; width: 100%; outline: none; }
  input:focus { border-color: #3b82f6; }
  .form-row { display: flex; gap: 8px; margin-top: 12px; }
  .form-row input { flex: 1; }
  button { padding: 8px 16px; border-radius: 8px; border: none; font-weight: 600; cursor: pointer; font-size: 0.875rem; }
  .btn-primary { background: #3b82f6; color: white; }
  .btn-primary:hover { background: #2563eb; }
  .btn-danger  { background: #ef444422; color: #f87171; border: 1px solid #ef444444; }
  .btn-danger:hover  { background: #ef444444; }
  #msg { margin-top: 8px; font-size: 0.8rem; color: #4ade80; min-height: 1.2em; }
</style>
</head>
<body>
<div class="header">
  <h1>📡 GSM Gateway PBX</h1>
  <span class="badge badge-green" id="mode-badge">Loading...</span>
</div>

<div class="grid">
  <div class="card">
    <h2>Gateway</h2>
    <div class="stat" id="gw-status">–</div>
  </div>
  <div class="card">
    <h2>Connected Clients</h2>
    <div class="stat" id="client-count">–</div>
  </div>
</div>

<div class="card" style="margin-bottom:20px">
  <h2>Live SIP Clients</h2>
  <table>
    <thead><tr><th>Extension</th><th>Contact</th><th>Status</th><th>Expires</th></tr></thead>
    <tbody id="clients-tbody"><tr><td colspan="4" style="color:#64748b">Loading...</td></tr></tbody>
  </table>
</div>

<div class="card">
  <h2>SIP Extensions</h2>
  <table>
    <thead><tr><th>Extension</th><th>Password</th><th></th></tr></thead>
    <tbody id="ext-tbody"><tr><td colspan="3" style="color:#64748b">Loading...</td></tr></tbody>
  </table>
  <div class="form-row">
    <input id="new-ext" placeholder="Extension (e.g. 102)" />
    <input id="new-pass" placeholder="Password" type="password" />
    <button class="btn-primary" onclick="addExt()">Add</button>
  </div>
  <div id="msg"></div>
</div>

<script>
async function loadStatus() {
  const r = await fetch('/api/status').then(r=>r.json()).catch(()=>null);
  if (!r) return;
  document.getElementById('gw-status').textContent = r.running ? 'Running' : 'Stopped';
  document.getElementById('mode-badge').textContent = r.local_server ? 'Server Mode' : 'Client Mode';
}

async function loadClients() {
  const clients = await fetch('/api/clients').then(r=>r.json()).catch(()=>[]);
  document.getElementById('client-count').textContent = clients.filter(c=>c.online).length;
  const tbody = document.getElementById('clients-tbody');
  if (!clients.length) { tbody.innerHTML='<tr><td colspan="4" style="color:#64748b">No clients registered</td></tr>'; return; }
  tbody.innerHTML = clients.map(c => `
    <tr>
      <td>${'$'}{c.extension}</td>
      <td style="font-size:0.8rem;color:#64748b">${'$'}{c.contact}</td>
      <td><span class="dot ${'$'}{c.online ? 'dot-green' : 'dot-grey'}"></span>${'$'}{c.online ? 'Online' : 'Offline'}</td>
      <td>${'$'}{c.expires_in}s</td>
    </tr>`).join('');
}

async function loadExtensions() {
  const exts = await fetch('/api/extensions').then(r=>r.json()).catch(()=>[]);
  const tbody = document.getElementById('ext-tbody');
  tbody.innerHTML = exts.map(e => `
    <tr>
      <td><strong>${'$'}{e.extension}</strong></td>
      <td>${'$'}{e.password}</td>
      <td><button class="btn-danger" onclick="delExt('${'$'}{e.extension}')">Delete</button></td>
    </tr>`).join('') || '<tr><td colspan="3" style="color:#64748b">No extensions</td></tr>';
}

async function addExt() {
  const ext = document.getElementById('new-ext').value.trim();
  const pass = document.getElementById('new-pass').value.trim();
  if (!ext || !pass) { showMsg('Extension and password required', '#f87171'); return; }
  const r = await fetch('/api/extensions', {method:'POST', body: JSON.stringify({extension:ext, password:pass}), headers:{'Content-Type':'application/json'}});
  if (r.ok) { showMsg('Extension added!', '#4ade80'); document.getElementById('new-ext').value=''; document.getElementById('new-pass').value=''; loadExtensions(); }
  else showMsg('Error adding extension', '#f87171');
}

async function delExt(ext) {
  await fetch('/api/extensions/' + ext, {method:'DELETE'});
  loadExtensions();
}

function showMsg(text, color) {
  const el = document.getElementById('msg');
  el.style.color = color;
  el.textContent = text;
  setTimeout(()=>el.textContent='', 3000);
}

function refresh() { loadStatus(); loadClients(); loadExtensions(); }
refresh();
setInterval(loadClients, 5000);
</script>
</body>
</html>
""".trimIndent()
}
