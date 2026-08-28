package com.example.aisecretary.alarms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.aisecretary.R

/**
 * Owns the alarm notification channel and the full-screen intent.
 *
 * Why this exists: Android 10+ blocks apps from opening a screen while in the background.
 * Calling startActivity() from a BroadcastReceiver is unreliable. The supported way to take
 * over a locked phone is to post a notification carrying a full-screen intent, which the
 * system launches on our behalf.
 */
object AlarmNotifier {

    const val CHANNEL_ID = "jarvix_alarm_channel"
    private const val CHANNEL_NAME = "Jarvix Alarms"

    /** Safe to call repeatedly - creating an existing channel is a no-op. */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Time-critical reminders from Jarvix"
            setSound(alarmSound, audioAttributes)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 500, 500)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun buildAlarmNotification(context: Context, alarmId: Int, title: String): Notification {
        val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(AlarmActivity.EXTRA_ALARM_ID, alarmId)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            alarmId,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("JARVIX")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    fun notify(context: Context, alarmId: Int, title: String) {
        ensureChannel(context)
        try {
            NotificationManagerCompat.from(context)
                .notify(alarmId, buildAlarmNotification(context, alarmId, title))
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted - AlarmActivity fallback still applies.
            e.printStackTrace()
        }
    }

    fun cancel(context: Context, alarmId: Int) {
        NotificationManagerCompat.from(context).cancel(alarmId)
    }
}
