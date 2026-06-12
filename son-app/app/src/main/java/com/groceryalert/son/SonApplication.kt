package com.groceryalert.son

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class SonApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "Grocery Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Grocery alert notifications from Mom"
                enableVibration(true)
                setShowBadge(true)
            }

            val wsChannel = NotificationChannel(
                WS_CHANNEL_ID,
                "WebSocket Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background connection status"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(alarmChannel)
            manager.createNotificationChannel(wsChannel)
        }
    }

    companion object {
        const val ALARM_CHANNEL_ID = "grocery_alarm_channel"
        const val WS_CHANNEL_ID = "websocket_channel"
    }
}
