package com.example.aisecretary.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.aisecretary.JarvixApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(AlarmActivity.EXTRA_ALARM_ID, -1)

        // onReceive runs on the main thread and must return fast, but we need a DB read.
        // goAsync() keeps the receiver alive while we do that off the main thread.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val title = try {
                    val app = context.applicationContext as JarvixApplication
                    app.taskRepository.getTaskById(alarmId)?.title ?: "Reminder"
                } catch (e: Exception) {
                    e.printStackTrace()
                    "Reminder"
                }

                // Primary path: a full-screen-intent notification. This is the only
                // supported way to take over a locked screen from the background.
                AlarmNotifier.notify(context, alarmId, title)

                // Secondary path: setAlarmClock() grants a temporary exemption from the
                // background-activity-start restriction, so try the direct launch too.
                // AlarmActivity is singleTask, so a double launch is harmless.
                try {
                    context.startActivity(
                        Intent(context, AlarmActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            putExtra(AlarmActivity.EXTRA_ALARM_ID, alarmId)
                        }
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
