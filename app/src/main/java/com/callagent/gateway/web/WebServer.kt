package com.callagent.gateway.web

import android.content.Context
import android.util.Log
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

object WebServer {
    private const val TAG = "KtorWebServer"
    private var engine: ApplicationEngine? = null

    fun start(context: Context, port: Int = 8080) {
        if (engine != null) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                engine = embeddedServer(CIO, port = port) {
                    routing {
                        get("/") {
                            call.respondText(getHtml(), ContentType.Text.Html)
                        }
                        
                        get("/api/status") {
                            val prefs = context.getSharedPreferences("gateway", Context.MODE_PRIVATE)
                            val status = JSONObject().apply {
                                put("server", prefs.getString("server", ""))
                                put("port", prefs.getInt("port", 5060))
                                put("user", prefs.getString("user", ""))
                                put("local_server", prefs.getBoolean("local_server", false))
                                put("running", true) // Assuming running if server is up
                            }
                            call.respondText(status.toString(), ContentType.Application.Json)
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

    private fun getHtml(): String {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>GSM Gateway PBX</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; background-color: #f4f4f9; color: #333; margin: 0; padding: 20px; }
                    .container { max-width: 600px; margin: 0 auto; background: white; padding: 30px; border-radius: 12px; box-shadow: 0 4px 15px rgba(0,0,0,0.05); }
                    h1 { color: #1a1a1a; margin-top: 0; }
                    .status-card { background: #e0f2f1; border-left: 5px solid #26a69a; padding: 15px; border-radius: 4px; margin-bottom: 20px; }
                    .status-value { font-weight: bold; color: #00796b; }
                    table { width: 100%; border-collapse: collapse; margin-top: 20px; }
                    th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }
                    th { background-color: #f8f9fa; }
                    .btn { display: inline-block; padding: 10px 20px; background-color: #2196f3; color: white; text-decoration: none; border-radius: 6px; font-weight: bold; cursor: pointer; border: none; }
                    .btn:hover { background-color: #1976d2; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>GSM Gateway PBX</h1>
                    
                    <div class="status-card">
                        <div>Gateway Status: <span class="status-value" id="gateway-status">Running</span></div>
                        <div style="margin-top: 8px;">Mode: <span class="status-value" id="gateway-mode">Loading...</span></div>
                    </div>

                    <h2>PBX SIP Extensions</h2>
                    <p>When running in Local PBX Mode, you can connect Zoiper directly to this device's IP using port 5060.</p>
                    
                    <table>
                        <tr>
                            <th>Extension</th>
                            <th>Password</th>
                            <th>Status</th>
                        </tr>
                        <tr>
                            <td id="ext-user">...</td>
                            <td>(Hidden)</td>
                            <td><span style="color: #4caf50;">Ready</span></td>
                        </tr>
                    </table>
                </div>

                <script>
                    fetch('/api/status')
                        .then(response => response.json())
                        .then(data => {
                            document.getElementById('gateway-mode').innerText = data.local_server ? 'Local PBX (Server)' : 'SIP Client';
                            document.getElementById('ext-user').innerText = data.user || 'Not configured';
                        });
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
