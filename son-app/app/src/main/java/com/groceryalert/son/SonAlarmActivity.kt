package com.groceryalert.son

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SonAlarmActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isBought = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true)
            setShowWhenLocked(true)
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        setContentView(R.layout.activity_son_alarm)

        val item = intent.getStringExtra("item") ?: "Unknown item"
        val isReminder = intent.getBooleanExtra("is_reminder", false)
        findViewById<TextView>(R.id.item_text).text = "Buy: $item"

        val reminderLabel = findViewById<TextView>(R.id.reminder_label)
        reminderLabel.text = if (isReminder) "Reminder!" else ""

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK
                    or PowerManager.ACQUIRE_CAUSES_WAKEUP
                    or PowerManager.ON_AFTER_RELEASE,
            "GroceryAlert:AlarmWakeLock"
        )
        wakeLock?.acquire(30_000)

        playAlarmSound()

        findViewById<Button>(R.id.dismiss_button).setOnClickListener {
            isBought = false
            dismissAlarm()
        }

        findViewById<Button>(R.id.bought_button).setOnClickListener {
            isBought = true
            dismissAlarm()
        }
    }

    private fun playAlarmSound() {
        try {
            val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(applicationContext, alarmUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
        }
    }

    private fun dismissAlarm() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null

        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(1001)

        val item = intent.getStringExtra("item") ?: ""
        val originalSender = intent.getStringExtra("from") ?: ""

        if (isBought) {
            SonReminderReceiver.cancelReminder(this)
            if (item.isNotEmpty() && originalSender.isNotEmpty()) {
                SonWebSocketClient.sendBoughtConfirmation(item, originalSender)
            }
        } else {
            if (item.isNotEmpty()) {
                SonReminderReceiver.scheduleReminder(this, item)
            }
        }

        finishAndRemoveTask()
    }

    override fun onDestroy() {
        dismissAlarm()
        super.onDestroy()
    }
}
