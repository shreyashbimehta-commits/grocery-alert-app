package com.groceryalert.son

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SonWebSocketService : Service() {

    companion object {
        private const val TAG = "SonWebSocket"
        private const val NOTIFICATION_ID = 9001
        const val SERVER_URL = "wss://grocery-alert-server.onrender.com"

        private const val BASE_DELAY_MS = 1_000L
        private const val MAX_DELAY_MS = 60_000L
    }

    private lateinit var client: OkHttpClient
    private var webSocket: WebSocket? = null
    private var reconnectAttempt = 0
    private var shouldReconnect = true
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastWakeLockAcquire = 0L

    override fun onCreate() {
        super.onCreate()
        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Connecting...")
        startForeground(NOTIFICATION_ID, notification)
        shouldReconnect = true
        connect()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        shouldReconnect = false
        webSocket?.close(1000, "Service destroyed")
        handler.removeCallbacksAndMessages(null)
        releaseWakeLock()
        super.onDestroy()
    }

    private fun connect() {
        if (!shouldReconnect) return

        val request = Request.Builder()
            .url(SERVER_URL)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Connected to server")
                reconnectAttempt = 0
                val reg = JSONObject().apply { put("role", "SON") }
                webSocket.send(reg.toString())
                updateNotification("Connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.i(TAG, "Received: $text")
                onWsMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "Closing: $code $reason")
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "Closed: $code $reason")
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Failure: ${t.message}", t)
                scheduleReconnect()
            }
        })
    }

    private fun onWsMessage(text: String) {
        try {
            val json = JSONObject(text)

            if (json.optString("status") == "registered") return

            val from = json.optString("from", json.optString("role"))
            if (from.isNullOrEmpty()) return

            val action = json.optString("action", "send")
            val item = json.optString("item", "")

            if (action == "bought") {
                Log.i(TAG, "$from bought: $item")
                val notification = NotificationCompat.Builder(this, SonApplication.ALARM_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("$from bought it!")
                    .setContentText("$item")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(1002, notification)
                return
            }

            Log.i(TAG, "Grocery alert received from $from: $item")

            val intent = Intent(this, SonAlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("item", item)
                putExtra("from", from)
            }

            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, SonApplication.ALARM_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Grocery Alert!")
                .setContentText("$from says: $item")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)
                .build()

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(1001, notification)

            startActivity(intent)
            acquireWakeLock()

        } catch (e: Exception) {
            Log.e(TAG, "Error handling message", e)
        }
    }

    private fun acquireWakeLock() {
        try {
            val now = System.currentTimeMillis()
            if (now - lastWakeLockAcquire < 5_000) return
            lastWakeLockAcquire = now

            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (wakeLock == null || !wakeLock!!.isHeld) {
                wakeLock = pm.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK
                            or PowerManager.ACQUIRE_CAUSES_WAKEUP
                            or PowerManager.ON_AFTER_RELEASE,
                    "GroceryAlert:AlarmWakeLock"
                )
                wakeLock?.acquire(10_000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock", e)
        }
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        reconnectAttempt++
        val delay = minOf(BASE_DELAY_MS * (1L shl (reconnectAttempt - 1)), MAX_DELAY_MS)
        Log.i(TAG, "Reconnecting in ${delay}ms (attempt $reconnectAttempt)")
        updateNotification("Reconnecting in ${delay / 1000}s...")
        handler.postDelayed({ connect() }, delay)
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, SonMainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, SonApplication.WS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Grocery Alert Service - Son")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
