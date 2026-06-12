package com.groceryalert.mom

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class MomApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmChannel = NotificationChannel(
                "mom_alarm_channel",
                "Grocery Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Grocery alert notifications"
                enableVibration(true)
                setShowBadge(true)
            }

            val wsChannel = NotificationChannel(
                "mom_ws_channel",
                "Mom Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background connection for confirmations"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(alarmChannel)
            manager.createNotificationChannel(wsChannel)
        }
    }
}
