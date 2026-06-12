package com.groceryalert.son

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SonWebSocketClient {
    private const val TAG = "SonWS"
    const val SERVER_URL = "wss://grocery-alert-server.onrender.com"

    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private var pendingCallback: ((Boolean, String) -> Unit)? = null
    private var closedCleanly = false

    fun sendAlert(item: String, target: String = "MOM", onResult: (Boolean, String) -> Unit) {
        pendingCallback = onResult
        closedCleanly = false

        val request = Request.Builder()
            .url(SERVER_URL)
            .build()

        client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val payload = JSONObject().apply {
                    put("role", "SON")
                    put("item", item)
                    put("target", target)
                }
                webSocket.send(payload.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val status = json.optString("status")
                    val msg = when (status) {
                        "delivered" -> {
                            val to = json.optString("to", target)
                            Pair(true, "Alert delivered to $to!")
                        }
                        "registered" -> return
                        else -> Pair(false, json.optString("message", "Unknown error"))
                    }
                    pendingCallback?.invoke(msg.first, msg.second)
                    pendingCallback = null
                } catch (e: Exception) {
                    pendingCallback?.invoke(false, "Parse error: ${e.message}")
                    pendingCallback = null
                }
                closedCleanly = true
                webSocket.close(1000, "Done")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!closedCleanly) {
                    pendingCallback?.invoke(false, "Connection failed: ${t.message}")
                    pendingCallback = null
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            }
        })

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            pendingCallback?.let { cb ->
                cb(false, "No response from server")
                pendingCallback = null
            }
        }, 15_000)
    }

    fun sendBoughtConfirmation(item: String, originalSender: String, onResult: ((Boolean, String) -> Unit)? = null) {
        pendingCallback = onResult
        closedCleanly = false

        val request = Request.Builder()
            .url(SERVER_URL)
            .build()

        client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val payload = JSONObject().apply {
                    put("role", "SON")
                    put("action", "bought")
                    put("item", item)
                    put("target", originalSender)
                }
                webSocket.send(payload.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val status = json.optString("status")
                    val action = json.optString("action")
                    when {
                        status == "delivered" && action == "bought" -> {
                            pendingCallback?.invoke(true, "Confirmation sent!")
                        }
                        status == "registered" -> return
                        else -> pendingCallback?.invoke(false, json.optString("message", "Unknown error"))
                    }
                    pendingCallback = null
                } catch (e: Exception) {
                    pendingCallback?.invoke(false, "Parse error: ${e.message}")
                    pendingCallback = null
                }
                closedCleanly = true
                webSocket.close(1000, "Done")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!closedCleanly) {
                    pendingCallback?.invoke(false, "Connection failed: ${t.message}")
                    pendingCallback = null
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            }
        })

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            pendingCallback?.let { cb ->
                cb(false, "No response from server")
                pendingCallback = null
            }
        }, 15_000)
    }
}
