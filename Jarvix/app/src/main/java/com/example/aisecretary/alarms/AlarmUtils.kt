package com.example.aisecretary.alarms

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object AlarmUtils {
    @SuppressLint("ScheduleExactAlarm")
    fun scheduleExactAlarm(context: Context, timeInMillis: Long, requestCode: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", requestCode)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(timeInMillis, null)
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
    }

    /**
     * Cancels a scheduled alarm.
     *
     * Without this, completing or deleting a task left its alarm armed in the system - the
     * reminder would still fire for something the user had already dealt with.
     *
     * The Intent must match the one used to schedule (same class + requestCode) for
     * AlarmManager to recognise it as the same PendingIntent.
     */
    fun cancelAlarm(context: Context, requestCode: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", requestCode)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}
