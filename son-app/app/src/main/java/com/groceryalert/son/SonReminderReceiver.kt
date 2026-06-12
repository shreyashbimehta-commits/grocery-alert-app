package com.groceryalert.son

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SonReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("grocery_reminder", Context.MODE_PRIVATE)
        val item = prefs.getString("pending_item", "") ?: ""
        if (item.isNotEmpty()) {
            val alarmIntent = Intent(context, SonAlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("item", item)
                putExtra("is_reminder", true)
            }
            context.startActivity(alarmIntent)
        }
    }

    companion object {
        private const val REMINDER_REQUEST_CODE = 3001

        fun scheduleReminder(context: Context, item: String) {
            val prefs = context.getSharedPreferences("grocery_reminder", Context.MODE_PRIVATE)
            prefs.edit().putString("pending_item", item).apply()

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, SonReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, REMINDER_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAt = System.currentTimeMillis() + 30 * 60 * 1000L
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }

        fun cancelReminder(context: Context) {
            val prefs = context.getSharedPreferences("grocery_reminder", Context.MODE_PRIVATE)
            prefs.edit().remove("pending_item").apply()

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, SonReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, REMINDER_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
